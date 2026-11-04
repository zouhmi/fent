import io
p = 'C:\\Users\\Admin\\Documents\\Fent-2\\api\\server.js'
s = io.open(p, encoding='utf-8', newline='').read()
markers = [
    "function sendJson(res, code, payload) {",
    "function checkKey(req) {",
    "function readBody(req) {",
    "function gateWrite(req, res) {",
]
for m in markers:
    idxs = []
    start = 0
    while True:
        i = s.find(m, start)
        if i == -1: break
        idxs.append(i)
        start = i + 1
    if len(idxs) > 1:
        print("DUP:", m, idxs)
print("---- occurrences of handleRequest:", s.count("async function handleRequest(req, res) {"))
print("---- occurrences of sseRegister:", s.count("function sseRegister()"))
print("---- occurrences of flushSse:", s.count("function flushSse"))
