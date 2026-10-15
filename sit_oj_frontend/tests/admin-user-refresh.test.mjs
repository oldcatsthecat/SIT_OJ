import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { parse, compileScript } from '@vue/compiler-sfc';
import { ref, reactive } from 'vue';

test('refresh button requests the current page without passing the click event', async () => {
  const source = readFileSync(new URL('../src/views/admin/UserManagement.vue', import.meta.url), 'utf8');
  const { descriptor, errors } = parse(source);
  assert.deepEqual(errors, []);
  const click = descriptor.template.content.match(/刷新数据<\/el-button>/);
  assert.ok(click, 'refresh button exists');
  const button = descriptor.template.content.slice(0, click.index);
  const expression = button.match(/<el-button[^>]*@click="([^"]+)"[^>]*>\s*$/)?.[1];
  assert.ok(expression, 'refresh button has a click handler');

  const requests = [];
  const errorsShown = [];
  const compiled = compileScript(descriptor, { id: 'UserManagement' });
  const context = {
    ref, reactive, onMounted() {},
    ElMessage: { error: message => errorsShown.push(message) },
    adminUserApi: { listAll: async params => {
      requests.push(params);
      if (!Number.isInteger(params.current)) throw new Error('current must be an integer');
      return { code: 200, data: { records: [{ id: 1 }], total: 1 } };
    } }
  };
  const code = compiled.content.replace(/^import .*;\r?$/gm, '').replace('export default', 'return');
  const component = new Function(...Object.keys(context), code)(...Object.values(context));
  const state = component.setup({}, { expose() {} });
  state.currentPage.value = 3;
  const event = { type: 'click' };
  const handler = new Function('$event', 'fetchUsers', 'currentPage', `return ${expression}`)(
    event, state.fetchUsers, state.currentPage.value
  );
  await (typeof handler === 'function' ? handler(event) : handler);

  assert.deepEqual(requests, [{ current: 3, size: 20 }]);
  assert.deepEqual(errorsShown, []);
  assert.deepEqual(state.users.value, [{ id: 1 }]);
});
