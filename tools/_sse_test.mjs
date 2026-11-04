// SSE smoke test for the new fent-api
const controller = new AbortController();

async function main() {
  const res = await fetch('http://127.0.0.1:6767/api/events', { signal: controller.signal });
  if (!res.ok) { console.log('SSE_FAIL', res.status); process.exit(1); }
  console.log('SSE_OPEN', res.status, res.headers.get('content-type'));
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buf = '';
  let firstFlush = true;
  const readNext = async () => {
    const { done, value } = await reader.read();
    if (done) return false;
    buf += decoder.decode(value, { stream: true });
    const idx = buf.indexOf('\n\n');
    if (idx >= 0) {
      const block = buf.slice(0, idx);
      buf = buf.slice(idx + 2);
      const lines = block.split('\n');
      let data = null;
      for (const l of lines) { if (l.startsWith('data:')) data = l.slice(5); }
      if (data) return data;
      return null;
    }
    return null;
  };
  // read hello (first event should be rev hello or nothing yet) - just wait a moment
  await new Promise(r => setTimeout(r, 1500));
  // publish a cosmetic
  const put = await fetch('http://127.0.0.1:6767/api/cosmetics/bob', { method: 'PUT', headers: { 'Content-Type': 'application/json', 'Fent-Key': process.env.FENT_KEY || '' }, body: JSON.stringify({ name: 'bob', items: { cape: 'FENT99' } }) });
  console.log('PUT_STATUS', put.status);
  let gotEvent = null;
  for (let i = 0; i < 20; i++) {
    const d = await readNext();
    if (d) { gotEvent = d; break; }
    await new Promise(r => setTimeout(r, 250));
  }
  console.log('SSE_EVENT=' + gotEvent);
  controller.abort();
  process.exit(0);
}
main().catch(e => { console.log('SSE_FAIL_ERR', e && e.message); process.exit(1); });
