import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const origin = process.env.OJ_ORIGIN || 'http://106.54.237.248';
const waitMs = Number(process.env.OJ_WAIT_MS || 5000);
const checkEditor = process.env.OJ_CHECK_EDITOR === '1';
const checkFallback = process.env.OJ_CHECK_FALLBACK === '1';
const browser = process.env.OJ_BROWSER || 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const profile = await mkdtemp(join(tmpdir(), 'oj-click-'));
const child = spawn(browser, ['--headless=new', '--no-first-run', '--disable-gpu', '--remote-debugging-port=0', `--user-data-dir=${profile}`, `${origin}/problems`], { stdio: 'ignore' });

try {
  let port;
  for (let i = 0; i < 100; i++) {
    try { port = Number((await readFile(join(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0]); break; }
    catch { await new Promise(resolve => setTimeout(resolve, 100)); }
  }
  assert.ok(port, 'Browser did not start');
  const page = (await (await fetch(`http://127.0.0.1:${port}/json/list`)).json()).find(target => target.type === 'page' && target.url.startsWith(origin));
  assert.ok(page, 'OJ page did not open');
  const ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }); });
  let sequence = 0;
  const waiting = new Map();
  const requests = [];
  const assets = [];
  const responses = [];
  const errors = [];
  let pausedEditorRequestId;
  ws.addEventListener('message', ({ data }) => {
    const message = JSON.parse(data);
    if (message.method === 'Fetch.requestPaused' && message.params.request.url.includes('/editor.main-')) pausedEditorRequestId = message.params.requestId;
    if (message.method === 'Network.requestWillBeSent' && message.params.request.url.includes('/api/prob')) requests.push(message.params.request.url);
    if (message.method === 'Network.requestWillBeSent' && message.params.request.url.includes('/assets/')) assets.push(message.params.request.url);
    if (message.method === 'Network.responseReceived' && message.params.response.url.includes('/assets/')) responses.push({ url: message.params.response.url, status: message.params.response.status });
    if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails.text);
    if (message.method === 'Network.loadingFailed') errors.push(message.params.errorText);
    if (waiting.has(message.id)) { waiting.get(message.id)(message); waiting.delete(message.id); }
  });
  const send = (method, params = {}) => new Promise(resolve => { const id = ++sequence; waiting.set(id, resolve); ws.send(JSON.stringify({ id, method, params })); });
  const evaluate = async expression => (await send('Runtime.evaluate', { expression, returnByValue: true })).result.result.value;
  await send('Network.enable');
  await send('Runtime.enable');
  if (checkFallback) await send('Fetch.enable', { patterns: [{ urlPattern: '*editor.main-*.js', requestStage: 'Request' }] });
  let link;
  for (let i = 0; i < 100; i++) {
    link = await evaluate('document.querySelector(".problem-name-link")?.textContent?.trim()');
    if (link) break;
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  if (!link) console.log(JSON.stringify({ url: await evaluate('location.href'), body: (await evaluate('document.body.innerText')).slice(0, 200), errors }));
  assert.ok(link, 'No problem link rendered');
  requests.length = 0;
  await evaluate('document.querySelector(".problem-name-link").click()');
  for (let i = 0; i < waitMs / 100 && !requests.some(url => /\/api\/problems\/\d+/.test(url)); i++) await new Promise(resolve => setTimeout(resolve, 100));
  const path = await evaluate('location.pathname');
  console.log(JSON.stringify({ link, path, detailRequests: requests.filter(url => /\/api\/problems\/\d+/.test(url)).length, assets: assets.slice(-8), responses: responses.slice(-8), errors }));
  assert.match(path, /^\/problem\/\d+$/, 'Click did not navigate to the detail route');
  assert.ok(requests.some(url => /\/api\/problems\/\d+/.test(url)), 'Click did not send a problem detail request');
  if (checkFallback) {
    for (let i = 0; i < 100 && !pausedEditorRequestId; i++) await new Promise(resolve => setTimeout(resolve, 100));
    assert.ok(pausedEditorRequestId, 'Advanced editor resource was not requested');
    const loadingShown = await evaluate('!!document.querySelector(".editor-loading .is-loading")');
    assert.ok(loadingShown, 'Loading indicator was not shown while the editor resource was pending');
    await send('Fetch.failRequest', { requestId: pausedEditorRequestId, errorReason: 'Failed' });
    let simpleReady = false;
    for (let i = 0; i < 150; i++) {
      simpleReady = await evaluate('!!document.querySelector("textarea.simple-editor")');
      if (simpleReady) break;
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    const canSubmit = await evaluate('!document.querySelector(".editor-header button.el-button--primary")?.disabled');
    console.log(JSON.stringify({ loadingShown, simpleReady, canSubmit }));
    assert.ok(simpleReady, 'Failed Monaco load did not show the simple editor');
    assert.ok(canSubmit, 'Simple editor cannot submit');
  }
  if (checkEditor) {
    let ready = false;
    for (let i = 0; i < 300; i++) {
      ready = await evaluate('!!document.querySelector("#code-editor .monaco-editor")');
      if (ready) break;
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    console.log(JSON.stringify({ editorReady: ready, simpleReady: await evaluate('!!document.querySelector("textarea.simple-editor")'), errors }));
    assert.ok(ready, 'Code editor did not initialize');
  }
  if (process.env.OJ_CHECK_LAYOUT === '1') {
    const layout = JSON.parse(await evaluate(`JSON.stringify({
      viewportHeight: window.innerHeight,
      pageHeight: document.documentElement.scrollHeight,
      maxScrollY: (window.scrollTo(0, 100000), window.scrollY),
      detailBottom: document.querySelector('.problem-detail')?.getBoundingClientRect().bottom,
      leftPanelScrollY: (() => {
        const body = document.querySelector('.left-panel .el-card__body');
        body.scrollTop = 100;
        return body.scrollTop;
      })()
    })`));
    console.log(JSON.stringify({ layout }));
    assert.ok(layout.detailBottom <= layout.viewportHeight + 1, 'Problem detail extends below the viewport');
    assert.equal(layout.maxScrollY, 0, 'Problem detail causes unnecessary page scrolling');
    assert.ok(layout.leftPanelScrollY > 0, 'Problem statement cannot scroll inside the left panel');
  }
  ws.close();
} finally {
  child.kill();
  await rm(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
}
