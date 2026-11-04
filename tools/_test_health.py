import json, urllib.request
BASE = 'http://127.0.0.1:6767'
r = urllib.request.Request(BASE + '/api/health', method='GET')
with urllib.request.urlopen(r, timeout=10) as resp:
    print(resp.status, resp.read().decode())
