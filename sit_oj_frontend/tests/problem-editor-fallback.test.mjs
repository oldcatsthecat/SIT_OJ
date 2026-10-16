import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { parse, compileScript } from '@vue/compiler-sfc';
import * as vue from 'vue';
import MarkdownIt from 'markdown-it';
import katex from 'katex';

function setupDetail(dependencies = {}) {
  const source = readFileSync(new URL('../src/views/ProblemDetail.vue', import.meta.url), 'utf8');
  const { descriptor, errors } = parse(source);
  assert.deepEqual(errors, []);
  const compiled = compileScript(descriptor, { id: 'ProblemDetail.vue' });
  const context = {
    ref: vue.ref, computed: vue.computed, nextTick: vue.nextTick,
    onMounted() {}, onBeforeUnmount() {}, MarkdownIt, katex,
    useRoute: () => ({ params: { id: '1' }, query: {} }),
    useRouter: () => ({ push() {} }),
    ElMessage: { warning() {}, error() {}, success() {} },
    localStorage: { getItem: () => 'test-token' },
    ...dependencies
  };
  for (const name of Object.keys(compiled.imports)) {
    if (!(name in context)) context[name] = {};
  }
  const code = compiled.content.replace(/^import .*;\r?$/gm, '').replace('export default', 'return');
  return new Function(...Object.keys(context), code)(...Object.values(context)).setup({}, { expose() {} });
}

test('failed advanced editor switches to a usable simple submission editor', async () => {
  const submissions = [];
  const state = setupDetail({
    submissionApi: { submit: async data => { submissions.push(JSON.parse(JSON.stringify(data))); return { code: 400, message: 'test response' }; } }
  });
  assert.equal(state.editorMode.value, 'monaco');
  assert.equal(state.editorStatus.value, 'loading');
  state.failEditor();
  assert.equal(state.editorMode.value, 'simple');
  assert.equal(state.editorStatus.value, 'failed');

  state.code.value = 'print(1 + 1)';
  state.submitForm.value.language = 'python';
  state.handleLanguageChange('python');
  assert.equal(state.code.value, 'print(1 + 1)', 'changing language preserves pasted code');
  await state.handleSubmit();
  assert.deepEqual(submissions, [{ problemId: '1', language: 'python', codeContent: 'print(1 + 1)', competitionId: null }]);
});

test('changing language replaces only an untouched starter template', () => {
  const state = setupDetail();
  state.submitForm.value.language = 'python';
  state.handleLanguageChange('python');
  assert.match(state.code.value, /^import sys/);
});

test('slow advanced editor yields to simple input without discarding the draft', () => {
  const state = setupDetail();
  state.code.value = 'int main() { return 0; }';
  state.slowEditor();
  assert.equal(state.editorMode.value, 'simple');
  assert.equal(state.editorStatus.value, 'slow');
  assert.equal(state.code.value, 'int main() { return 0; }');
});

test('simple editor submits contest code through the contest API', async () => {
  const submissions = [];
  const state = setupDetail({
    useRoute: () => ({ params: { id: '3' }, query: { cid: '7' } }),
    competitionApi: { submit: async (cid, data) => {
      submissions.push({ cid, data: JSON.parse(JSON.stringify(data)) });
      return { code: 400, message: 'test response' };
    } }
  });
  state.failEditor();
  state.code.value = 'int main() { return 0; }';
  await state.handleSubmit();
  assert.deepEqual(submissions, [{ cid: '7', data: {
    problemId: '3', language: 'cpp', codeContent: 'int main() { return 0; }', competitionId: 7
  } }]);
});
