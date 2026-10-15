import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { parse, compileScript } from '@vue/compiler-sfc';
import * as vue from 'vue';
import dayjs from 'dayjs';
import MarkdownIt from 'markdown-it';
import katex from 'katex';

// Exercise the real SFC setup with external services and browser lifecycle stubbed.
function setupView(file, dependencies) {
  const source = readFileSync(new URL(`../src/views/${file}`, import.meta.url), 'utf8');
  const { descriptor, errors } = parse(source);
  assert.deepEqual(errors, []);
  const compiled = compileScript(descriptor, { id: file });
  const context = {
    ref: vue.ref, computed: vue.computed, reactive: vue.reactive, nextTick: vue.nextTick,
    dayjs, MarkdownIt, katex,
    onMounted() {}, onUnmounted() {}, onBeforeUnmount() {},
    ...dependencies
  };
  for (const name of Object.keys(compiled.imports)) {
    if (!(name in context)) context[name] = {};
  }
  const code = compiled.content.replace(/^import .*;\r?$/gm, '').replace('export default', 'return');
  const component = new Function(...Object.keys(context), code)(...Object.values(context));
  return component.setup({}, { expose() {} });
}

const flush = () => new Promise(resolve => setImmediate(resolve));

for (const loggedIn of [false, true]) {
  for (const phase of ['upcoming', 'running', 'finished']) {
    test(`unregistered ${loggedIn ? 'user' : 'guest'} can load and paginate ${phase} standings`, async () => {
      const rankCalls = [];
      const startTime = dayjs().add(phase === 'upcoming' ? 1 : -2, 'hour').toISOString();
      const endTime = dayjs().add(phase === 'finished' ? -1 : 2, 'hour').toISOString();
      const state = setupView('CompetitionDetail.vue', {
        localStorage: { getItem: () => loggedIn ? 'test-token' : null },
        useRoute: () => ({ params: { id: '7' }, query: { tab: 'rank' } }),
        useRouter: () => ({ replace() {} }),
        competitionApi: {
          getById: async () => ({ code: 200, data: {
            startTime, endTime, isRegistered: false, problems: [{ problemId: 1 }]
          } }),
          getProblemStats: async () => ({ code: 200, data: {} }),
          getRank: async (id, page) => {
            rankCalls.push({ id, ...page });
            return { code: 200, data: { records: [{ userId: 10 }], total: 25,
              isFrozen: true, frozenAttempts: { 10: { 1: 2 } } } };
          },
          register: () => assert.fail('Reading standings must not register the viewer')
        },
        submissionApi: { getCompetitionSubmissions: () => assert.fail('Submissions are still gated') }
      });
      await state.fetchData();
      await flush();
      assert.equal(state.canViewProblems.value, false);
      assert.equal(rankCalls.length, 1, 'Opening ?tab=rank loads standings');
      assert.deepEqual(state.rankList.value, [{ userId: 10 }]);
      assert.equal(state.isFrozen.value, true);
      assert.deepEqual(state.frozenAttempts.value, { 10: { 1: 2 } });
      await state.fetchRank(2);
      assert.deepEqual(rankCalls[1], { id: '7', current: 2, size: 20 });
      assert.equal(state.rankPage.total, 25);
      state.handleTabChange('rank');
      await flush();
      assert.equal(rankCalls.length, 3, 'Switching to the rank tab loads standings');
      state.handleTabChange('submissions');
      await state.fetchCompSubmissions();
    });
  }
}

for (const cid of [undefined, '7']) {
  test(`displayed limits follow the selected language (${cid ? 'contest' : 'practice'})`, () => {
    const state = setupView('ProblemDetail.vue', {
      useRoute: () => ({ params: { id: '1' }, query: { cid } }),
      useRouter: () => ({})
    });
    state.problem.value = { timeLimit: 1200, memoryLimit: 128 };
    assert.deepEqual(state.effectiveLimits.value, { timeLimit: 1200, memoryLimit: 128 });
    for (const language of ['python', 'java']) {
      state.submitForm.value.language = language;
      assert.deepEqual(state.effectiveLimits.value, { timeLimit: 2400, memoryLimit: 256 });
      state.problem.value.memoryLimit = 384;
      assert.deepEqual(state.effectiveLimits.value, { timeLimit: 2400, memoryLimit: 512 });
      state.problem.value.memoryLimit = 128;
    }
    state.submitForm.value.language = 'c';
    assert.deepEqual(state.effectiveLimits.value, { timeLimit: 1200, memoryLimit: 128 });
  });
}
