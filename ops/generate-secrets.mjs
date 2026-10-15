import { randomBytes } from 'node:crypto';
import { existsSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
const root = new URL('../', import.meta.url);
const target = new URL('.env', root);
if (existsSync(target)) throw new Error('.env already exists; keep it to avoid accidental credential rotation.');
const hex = () => randomBytes(32).toString('hex');
const env = {
 JWT_SECRET: hex(), INTERNAL_API_KEY: hex(),
 MYSQL_ROOT_PASSWORD: hex(), DB_USERNAME: 'oj_app', DB_PASSWORD: hex(),
 RABBITMQ_PASSWORD: hex(), REDIS_PASSWORD: hex(), JUDGE_TOKEN: hex(),
 NACOS_PASSWORD: hex(), NACOS_AUTH_TOKEN: randomBytes(48).toString('base64'),
 NACOS_AUTH_IDENTITY_VALUE: hex(),
 MAIL_ENABLED: 'false', MAIL_USERNAME: '', MAIL_PASSWORD: ''
};
writeFileSync(target, '# PRIVATE: do not commit, paste into public issues, or serve this file.\n' +
 Object.entries(env).map(([k,v]) => k+'='+v).join('\n')+'\n', {mode:0o600, flag:'wx'});
console.log('Generated private credentials in '+fileURLToPath(target)+'. Email remains disabled until a new QQ authorization code is configured.');
