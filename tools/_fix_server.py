import io
p = 'C:\\Users\\Admin\\Documents\\Fent-2\\api\\server.js'
s = io.open(p, encoding='utf-8', newline='').read()

marker = "async function handleRequest(req, res) {"
first = s.find(marker)
second = s.find(marker, first + 1)
assert first != -1 and second != -1, (first, second)
removed = s[first:second]
assert "sendJson(res, 404, { error: 'not found' });" in removed, removed[-400:]
s = s[:first] + s[second:]
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print("OK removed duplicate first handleRequest block")
