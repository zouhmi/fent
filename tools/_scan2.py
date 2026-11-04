import io
p = 'C:\\Users\\Admin\\Documents\\Fent-2\\api\\server.js'
s = io.open(p, encoding='utf-8', newline='').read()
import re
for m in re.finditer(r'^(?:async )?function \w+|^const \w+ = |^async function \w+|^function \w+', s, re.M):
    print(m.start(), m.group(0))
print('---- total lines:', s.count('\n'))
