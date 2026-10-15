import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { parse, compileScript } from '@vue/compiler-sfc';
import { ref, computed, nextTick } from 'vue';
import dayjs from 'dayjs';

function setupExport(api = {}) {
  const { descriptor } = parse(readFileSync(new URL('../src/views/admin/AdminCompetition.vue', import.meta.url), 'utf8'));
  const compiled = compileScript(descriptor, { id: 'export-tests' });
  const requests = [], downloads = [], messages = [];
  const context = {
    ref, computed, nextTick, dayjs, onMounted() {},
    ElMessage: { success: text => messages.push(text), error: text => messages.push(text) },
    adminCompetitionApi: {
      getResolverParticipants: async () => ({ code: 200, data: [
        { userId: 1, username: 'alice', realName: '甲' },
        { userId: 2, username: 'bob', realName: '乙' }
      ] }),
      exportResolver: async (id, userGroups) => {
        requests.push({ id, userGroups });
        return { code: 200, data: '{"type":"state","data":{}}\n' };
      },
      ...api
    },
    Blob, URL: { createObjectURL: () => 'blob:test', revokeObjectURL() {} },
    document: { createElement: () => {
      const anchor = { click: () => downloads.push(anchor.download) };
      return anchor;
    } }
  };
  for (const name of Object.keys(compiled.imports)) if (!(name in context)) context[name] = {};
  const code = compiled.content.replace(/^import .*;\r?$/gm, '').replace('export default', 'return');
  const component = new Function(...Object.keys(context), code)(...Object.values(context));
  return { state: component.setup({}, { expose() {} }), requests, downloads, messages };
}

test('opening export defaults every user to participants without downloading', async () => {
  const { state, requests } = setupExport();
  await state.openExportDialog({ competitionId: 1, competitionName: '比赛' });
  assert.equal(state.exportDialogVisible.value, true);
  assert.equal(state.participantCount.value, 2);
  assert.equal(state.starCount.value, 0);
  assert.deepEqual(state.exportParticipants.value.map(user => user.group), ['participants', 'participants']);
  assert.equal(requests.length, 0);
});

test('filtered search preserves selections and exports every user', async () => {
  const { state, requests, downloads } = setupExport();
  await state.openExportDialog({ competitionId: 7 });
  state.exportParticipants.value[0].group = 'stars';
  state.exportSearch.value = 'BOB';
  assert.equal(state.filteredExportParticipants.value.length, 1);
  assert.equal(state.starCount.value, 1);
  assert.equal(state.participantCount.value, 1);
  await state.handleExport();
  assert.deepEqual(requests, [{ id: 7, userGroups: { 1: 'stars', 2: 'participants' } }]);
  assert.deepEqual(downloads, ['contest_7_event-feed.ndjson']);
  assert.equal(state.exportDialogVisible.value, false);
  assert.equal(state.exporting.value, false);
});

test('all stars can be exported', async () => {
  const { state, requests } = setupExport();
  await state.openExportDialog({ competitionId: 1 });
  state.exportParticipants.value.forEach(user => user.group = 'stars');
  await state.handleExport();
  assert.equal(state.participantCount.value, 0);
  assert.deepEqual(requests[0].userGroups, { 1: 'stars', 2: 'stars' });
});

test('cancel and opening another competition do not retain prior groups', async () => {
  const { state, requests } = setupExport();
  await state.openExportDialog({ competitionId: 1 });
  state.exportParticipants.value[0].group = 'stars';
  state.exportDialogVisible.value = false;
  await state.openExportDialog({ competitionId: 2 });
  assert.equal(state.starCount.value, 0);
  assert.equal(requests.length, 0);
});

test('a failed or malformed participant response blocks export', async () => {
  for (const response of [{ code: 500, message: '比赛不存在' }, { code: 200, data: {} }]) {
    const { state, requests } = setupExport({ getResolverParticipants: async () => response });
    await state.openExportDialog({ competitionId: 1 });
    assert.ok(state.exportLoadError.value);
    await state.handleExport();
    assert.equal(requests.length, 0);
  }
});

test('a stale roster response cannot overwrite the current competition', async () => {
  let resolveFirst;
  const { state } = setupExport({ getResolverParticipants: id => id === 1
    ? new Promise(resolve => { resolveFirst = resolve; })
    : Promise.resolve({ code: 200, data: [{ userId: 9, username: 'new' }] }) });
  const first = state.openExportDialog({ competitionId: 1 });
  await state.openExportDialog({ competitionId: 2 });
  resolveFirst({ code: 200, data: [{ userId: 1 }] });
  await first;
  assert.equal(state.exportCompetition.value.competitionId, 2);
  assert.deepEqual(state.exportParticipants.value.map(user => user.userId), [9]);
});

test('failed export keeps the selection available for retry', async () => {
  const { state, downloads, messages } = setupExport({
    exportResolver: async () => ({ code: 500, message: '请刷新名单后重试' })
  });
  await state.openExportDialog({ competitionId: 1 });
  state.exportParticipants.value[0].group = 'stars';
  await state.handleExport();
  assert.equal(state.exportDialogVisible.value, true);
  assert.equal(state.exportParticipants.value[0].group, 'stars');
  assert.equal(state.exporting.value, false);
  assert.deepEqual(downloads, []);
  assert.deepEqual(messages, ['请刷新名单后重试']);
});

test('an empty roster remains exportable', async () => {
  const { state, requests } = setupExport({ getResolverParticipants: async () => ({ code: 200, data: [] }) });
  await state.openExportDialog({ competitionId: 1 });
  await state.handleExport();
  assert.deepEqual(requests, [{ id: 1, userGroups: {} }]);
});
