import io
p = 'C:\\Users\\Admin\\Documents\\Fent-2\\net\\silentclient\\fent\\mixin\\FentCosmeticTypeMixin.java'
s = io.open(p, encoding='utf-8', newline='').read()
i = s.find('String saved = null;')
print(repr(s[i - 60:i + 320]))
