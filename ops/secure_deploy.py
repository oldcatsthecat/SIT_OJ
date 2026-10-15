#!/usr/bin/env python3
"""Run locally on the OJ server. Does not enable public access or change firewalls."""
import base64
import datetime
import getpass
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
CORE_KEYS = ("JWT_SECRET", "INTERNAL_API_KEY", "MYSQL_ROOT_PASSWORD", "DB_PASSWORD",
             "RABBITMQ_PASSWORD", "REDIS_PASSWORD", "JUDGE_TOKEN", "NACOS_PASSWORD", "NACOS_AUTH_IDENTITY_VALUE")

def read_env():
    values = {}
    for line in (ROOT / ".env").read_text(encoding="utf-8-sig").splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        key, sep, value = line.partition("=")
        if not sep or not re.fullmatch(r"[A-Z_]+", key):
            raise RuntimeError("Invalid .env format")
        values[key] = value
    for key in CORE_KEYS:
        if not re.fullmatch(r"[0-9a-f]{64}", values.get(key, "")):
            raise RuntimeError(key + " must be generated with ops/generate-secrets.mjs")
    if len(set(values[key] for key in CORE_KEYS)) != len(CORE_KEYS):
        raise RuntimeError("Credentials must be independent")
    if len(base64.b64decode(values.get("NACOS_AUTH_TOKEN", ""), validate=True)) < 32:
        raise RuntimeError("Invalid NACOS_AUTH_TOKEN")
    if not re.fullmatch(r"[a-z][a-z0-9_]{0,31}", values.get("DB_USERNAME", "")):
        raise RuntimeError("Invalid DB_USERNAME")
    if values.get("MAIL_ENABLED") == "true" and not all(values.get(k) for k in ("MAIL_USERNAME", "MAIL_PASSWORD")):
        raise RuntimeError("Configure a NEW QQ authorization code before enabling email")
    return values

def run(args, data=None, output=None):
    result = subprocess.run(args, input=data, stdout=output if output else subprocess.PIPE,
                            stderr=subprocess.PIPE, cwd=ROOT)
    if result.returncode:
        # Never print command arguments, SQL, environment or raw stderr containing credentials.
        raise RuntimeError("Command failed: " + args[0] + " " + args[1] + " (inspect local service status)")
    return result.stdout or b""

def exists(name):
    return subprocess.run(["docker", "inspect", name], stdout=subprocess.DEVNULL,
                          stderr=subprocess.DEVNULL).returncode == 0

def mysql(password, sql):
    script = 'IFS= read -r MYSQL_PWD; export MYSQL_PWD; exec mysql -uroot --batch --skip-column-names'
    return run(["docker", "exec", "-i", "oj-mysql", "sh", "-c", script],
               (password + "\n" + sql + "\n").encode()).decode().strip()

def current_db_password(values):
    # Existing container environment is read only into memory, never printed or written.
    config = json.loads(run(["docker", "inspect", "oj-mysql"]).decode())[0]
    old = dict(s.split("=", 1) for s in config["Config"]["Env"] if "=" in s).get("MYSQL_ROOT_PASSWORD", "")
    for candidate in dict.fromkeys((old, values["MYSQL_ROOT_PASSWORD"])):
        if candidate:
            try:
                mysql(candidate, "SELECT 1;")
                return candidate
            except RuntimeError:
                pass
    candidate = getpass.getpass("Current MySQL root password (not saved): ")
    mysql(candidate, "SELECT 1;")
    return candidate

def preserve_rabbit_identity(values):
    # RabbitMQ's data directory includes its hostname; preserve the old node when recreating it.
    if not exists("oj-rabbitmq"):
        data = ROOT / "data/rabbitmq"
        if data.exists() and any(data.iterdir()) and not values.get("RABBITMQ_HOSTNAME"):
            raise RuntimeError("Existing RabbitMQ data has no container or saved hostname. Restore the old container first.")
        return
    config = json.loads(run(["docker", "inspect", "oj-rabbitmq"]).decode())[0]
    hostname = config["Config"]["Hostname"]
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9.-]{0,252}", hostname):
        raise RuntimeError("Unexpected RabbitMQ hostname")
    environment = dict(s.split("=", 1) for s in config["Config"]["Env"] if "=" in s)
    if environment.get("RABBITMQ_NODENAME", "rabbit@" + hostname) != "rabbit@" + hostname:
        raise RuntimeError("Custom RabbitMQ node name needs an explicit migration; refusing to replace its data identity")
    lines = (ROOT / ".env").read_text(encoding="utf-8-sig").splitlines()
    lines = [line for line in lines if not line.startswith("RABBITMQ_HOSTNAME=")]
    lines.append("RABBITMQ_HOSTNAME=" + hostname)
    (ROOT / ".env").write_text("\n".join(lines) + "\n", encoding="utf-8")
    values["RABBITMQ_HOSTNAME"] = hostname


def preserve_and_rotate(values):
    if not exists("oj-mysql"):
        if (ROOT / "data/mysql/mysql").exists():
            raise RuntimeError("Existing database has no container. Restore/start the old MySQL container before migration.")
        return
    password = current_db_password(values)
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    backup = ROOT / "incident-backups" / stamp
    backup.mkdir(parents=True, mode=0o700, exist_ok=False)
    names = run(["docker", "ps", "-a", "--format", "{{.Names}}"]).decode().splitlines()
    for name in names:
        if name.startswith("oj-"):
            with (backup / (name + ".log")).open("xb") as output:
                subprocess.run(["docker", "logs", "--timestamps", name], stdout=output, stderr=subprocess.STDOUT)
    applications = [n for n in names if n in {
        "oj-frontend", "oj-gateway", "oj-user", "oj-problem", "oj-submission",
        "oj-competition", "oj-judge-logic", "oj-judge-server"}]
    if applications:
        run(["docker", "stop"] + applications)
    with (backup / "sit_oj.sql").open("xb") as output:
        script = 'IFS= read -r MYSQL_PWD; export MYSQL_PWD; exec mysqldump -uroot --single-transaction --routines --triggers --databases sit_oj'
        run(["docker", "exec", "-i", "oj-mysql", "sh", "-c", script], (password + "\n").encode(), output)
    if (backup / "sit_oj.sql").stat().st_size < 100:
        raise RuntimeError("Database backup is empty; refusing credential changes")
    print("Saved private incident evidence and database snapshot:", backup)
    if exists("oj-nacos") and not (ROOT / "data/nacos").exists():
        run(["docker", "cp", "oj-nacos:/home/nacos/data", str(ROOT / "data/nacos")])
    username = values["DB_USERNAME"]
    app_password = values["DB_PASSWORD"]
    mysql(password, f"CREATE USER IF NOT EXISTS '{username}'@'%' IDENTIFIED BY '{app_password}';\n"
                    f"ALTER USER '{username}'@'%' IDENTIFIED BY '{app_password}';\n"
                    f"REVOKE ALL PRIVILEGES, GRANT OPTION FROM '{username}'@'%';\n"
                    f"GRANT SELECT, INSERT, UPDATE, DELETE ON sit_oj.* TO '{username}'@'%';")
    # One connection updates every root host entry, so a localhost change cannot lock out later statements.
    hosts = mysql(password, "SELECT Host FROM mysql.user WHERE User='root';").splitlines()
    if not hosts:
        raise RuntimeError("No root accounts found")
    sql = []
    for host in hosts:
        host = host.replace("\\", "\\\\").replace("'", "''")
        sql.append(f"ALTER USER 'root'@'{host}' IDENTIFIED BY '{values['MYSQL_ROOT_PASSWORD']}';")
    mysql(password, "\n".join(sql))
    mysql(values["MYSQL_ROOT_PASSWORD"], "SELECT 1;")
    if exists("oj-rabbitmq"):
        run(["docker", "exec", "-i", "oj-rabbitmq", "sh", "-c",
             'IFS= read -r password; exec rabbitmqctl change_password oj_admin "$password"'],
            (values["RABBITMQ_PASSWORD"] + "\n").encode())
    print("Existing MySQL and RabbitMQ credentials updated. Existing records and queues retained.")

def nacos_request(path, fields, method="POST"):
    request = urllib.request.Request("http://127.0.0.1:8848/nacos/v1/auth/" + path,
                data=urllib.parse.urlencode(fields).encode(), method=method,
                headers={"Content-Type": "application/x-www-form-urlencoded"})
    with urllib.request.urlopen(request, timeout=5) as response:
        body = response.read()
    try:
        return json.loads(body)
    except json.JSONDecodeError:
        return {"message": body.decode(errors="replace")}

def initialize_nacos(values):
    new = values["NACOS_PASSWORD"]
    for _ in range(30):
        try:
            nacos_request("users/login", {"username": "nacos", "password": new})
            break
        except urllib.error.HTTPError:
            break
        except (urllib.error.URLError, TimeoutError):
            time.sleep(2)
    def login(password):
        try:
            return nacos_request("users/login", {"username": "nacos", "password": password}).get("accessToken")
        except urllib.error.HTTPError:
            return None
    if login(new):
        print("Nacos credentials verified.")
        return
    token = login("nacos")
    if not token:
        token = login(getpass.getpass("Current Nacos password (not saved): "))
    if not token:
        raise RuntimeError("Cannot authenticate Nacos administrator")
    nacos_request("users?accessToken=" + urllib.parse.quote(token, safe=""),
                  {"username": "nacos", "newPassword": new}, method="PUT")
    if not login(new):
        raise RuntimeError("Nacos credential verification failed")
    print("Nacos administrator credential rotated.")

def main():
    os.umask(0o077)
    values = read_env()
    preserve_rabbit_identity(values)
    # Parent-shell variables must not silently override the private .env deployment values.
    os.environ.update(values)
    os.chdir(ROOT)
    os.chmod(ROOT / ".env", 0o600)
    prepare_log_directories()
    run(["docker", "compose", "config", "--quiet"])
    # Build before stopping anything; never deploy stale JARs after Maven failure.
    run(["mvn", "clean", "package", "-f", "sit_oj_backend/pom.xml"])
    preserve_and_rotate(values)
    run(["docker", "compose", "up", "-d", "--wait", "--wait-timeout", "180",
         "oj-mysql", "oj-redis", "oj-rabbitmq", "oj-nacos"])
    initialize_nacos(values)
    run(["docker", "compose", "up", "-d", "--build"])
    print("Deployment started. Keep public ingress closed until the checks in SECURITY-DEPLOY.md pass.")
    print("Mail enabled:", values.get("MAIL_ENABLED", "false"))
    print("Existing JWTs are invalid after ALL application services have been replaced.")


def prepare_log_directories():
    # The current images run with a root master process; log directories remain private on the host.
    directory = ROOT / "data/logs"
    if directory.is_symlink():
        raise RuntimeError("Refusing a symlink as the persistent log directory")
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(directory, 0o700)
    for service in ("nginx", "gateway", "user", "problem", "submission", "competition", "judge"):
        path = directory / service
        if path.is_symlink():
            raise RuntimeError("Refusing a symlink as a service log directory")
        path.mkdir(exist_ok=True, mode=0o700)
        os.chmod(path, 0o700)

if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Some exceptions (notably URLs) may contain a Nacos access token.
        print("Secure deployment stopped:", str(error) if isinstance(error, RuntimeError)
              else type(error).__name__ + ". Check local service status; credentials were not printed.", file=sys.stderr)
        sys.exit(1)
