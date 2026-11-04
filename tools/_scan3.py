import io
s = io.open('C:\\Users\\Admin\\Documents\\Fent-2\\api\\server.js', encoding='utf-8', newline='').read()
import re
for m in re.finditer(r'.{0,60}async function handleRequest\(req, res\) \{', s):
    print(m.start(), repr(m.group(0)))
print('count', s.count('async function handleRequest(req, res) {'))
print('len', len(s))
