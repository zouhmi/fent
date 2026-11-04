import io
p = 'C:\\Users\\Admin\\Documents\\Fent-2\\api\\server.js'
s = io.open(p, encoding='utf-8', newline='').read()
old = """function health() {
  let users = 0;
  for (const row of cosmetics.values()) users++;
  let emotes = 0;
  for (const row of emotes.values()) if (row.emote) emotes++;
  return { service: 'fent-api', ok: true, users, emotes, rev: Number(meta.rev) || 0, erev: Number(meta.erev) || 0, sse_clients: sseClients.size, time: Date.now() };
}"""
new = """function health() {
  let userCount = 0;
  for (const row of cosmetics.values()) userCount++;
  let emoteCount = 0;
  for (const row of emotes.values()) if (row.emote) emoteCount++;
  return { service: 'fent-api', ok: true, users: userCount, emotes: emoteCount, rev: Number(meta.rev) || 0, erev: Number(meta.erev) || 0, sse_clients: sseClients.size, time: Date.now() };
}"""
assert s.count(old) == 1, s.count(old)
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('OK')
