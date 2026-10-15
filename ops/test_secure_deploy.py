import base64
import importlib.util
import json
from pathlib import Path
import secrets
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("secure_deploy", Path(__file__).with_name("secure_deploy.py"))
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)

class DeploymentTests(unittest.TestCase):
    def test_preparing_log_mounts_preserves_existing_records(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(deploy, "ROOT", root):
                deploy.prepare_log_directories()
                log = root / "data/logs/submission/audit.ndjson"
                log.write_text('{"requestId":"existing"}\n', encoding="utf-8")
                deploy.prepare_log_directories()
            self.assertEqual('{"requestId":"existing"}\n', log.read_text(encoding="utf-8"))
            self.assertEqual(7, len(list((root / "data/logs").iterdir())))

    def values(self):
        v = {k: secrets.token_hex(32) for k in deploy.CORE_KEYS}
        v.update(NACOS_AUTH_TOKEN=base64.b64encode(secrets.token_bytes(48)).decode(),
                 DB_USERNAME="oj_app", MAIL_ENABLED="false", MAIL_USERNAME="", MAIL_PASSWORD="")
        return v

    def read_values(self, values):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / ".env").write_text("\n".join(k+"="+v for k,v in values.items()), encoding="utf-8")
            with patch.object(deploy, "ROOT", root):
                return deploy.read_env()

    def test_accepts_independent_generated_credentials(self):
        self.assertEqual(self.read_values(self.values())["DB_USERNAME"], "oj_app")

    def test_rejects_shared_credentials(self):
        v = self.values()
        v["DB_PASSWORD"] = v["JWT_SECRET"]
        with self.assertRaises(RuntimeError):
            self.read_values(v)

    def test_mail_needs_new_credentials_when_enabled(self):
        v = self.values()
        v["MAIL_ENABLED"] = "true"
        with self.assertRaises(RuntimeError):
            self.read_values(v)

    def test_rejects_unsafe_database_user(self):
        v = self.values()
        v["DB_USERNAME"] = "user'; DROP USER root;--"
        with self.assertRaises(RuntimeError):
            self.read_values(v)

    def test_mysql_sends_password_and_sql_on_stdin(self):
        secret = secrets.token_hex(32)
        with patch.object(deploy, "run", return_value=b"1\n") as run:
            self.assertEqual(deploy.mysql(secret, "SELECT 1;"), "1")
        args, stdin = run.call_args.args
        self.assertNotIn(secret, " ".join(args))
        self.assertEqual(stdin, (secret+"\nSELECT 1;\n").encode())

    def test_refuses_existing_volume_without_container(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "data/mysql/mysql").mkdir(parents=True)
            with patch.object(deploy, "ROOT", root), patch.object(deploy, "exists", return_value=False):
                with self.assertRaises(RuntimeError):
                    deploy.preserve_and_rotate(self.values())

    def test_uses_existing_database_password_without_logging_it(self):
        old = secrets.token_hex(32)
        config = [{"Config": {"Env": ["MYSQL_ROOT_PASSWORD="+old]}}]
        with patch.object(deploy, "run", return_value=json.dumps(config).encode()), patch.object(deploy, "mysql") as mysql:
            self.assertEqual(deploy.current_db_password(self.values()), old)
            mysql.assert_called_once_with(old, "SELECT 1;")

    def test_preserves_existing_rabbit_hostname_without_copying_old_secrets(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / ".env").write_text("JWT_SECRET=new-value\n", encoding="utf-8")
            config = [{"Config": {"Hostname": "old-node-123", "Env": ["RABBITMQ_DEFAULT_PASS=old-secret"]}}]
            values = self.values()
            with patch.object(deploy, "ROOT", root), patch.object(deploy, "exists", return_value=True), patch.object(deploy, "run", return_value=json.dumps(config).encode()):
                deploy.preserve_rabbit_identity(values)
            self.assertEqual(values["RABBITMQ_HOSTNAME"], "old-node-123")
            saved = (root / ".env").read_text()
            self.assertIn("RABBITMQ_HOSTNAME=old-node-123", saved)
            self.assertNotIn("old-secret", saved)

    def test_refuses_unidentified_existing_rabbit_data(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            data = root / "data/rabbitmq"
            data.mkdir(parents=True)
            (data / "mnesia").mkdir()
            with patch.object(deploy, "ROOT", root), patch.object(deploy, "exists", return_value=False):
                with self.assertRaises(RuntimeError):
                    deploy.preserve_rabbit_identity(self.values())

if __name__ == "__main__":
    unittest.main()
