import io
p = 'C:\\Users\\Admin\\Documents\\Fent-2\\api\\server.js'
s = io.open(p, encoding='utf-8', newline='').read()
first = 6804
second = 11707
assert s[first:first+30].startswith('async function handleRequest'), repr(s[first:first+40])
assert s[second:second+30].startswith('async function handleRequest'), repr(s[second:second+40])
s2 = s[:first] + s[second:]
io.open(p, 'w', encoding='utf-8', newline='').write(s2)
print('OK')
