import json, urllib.request, urllib.error
BASE = 'http://127.0.0.1:6767'
r = urllib.request.Request(BASE + '/api/health', method='GET')
try:
    with urllib.request.urlopen(r, timeout=10) as resp:
        print(resp.status, resp.read().decode())
except urllib.error.HTTPError as e:
    print(e.code, e.read().decode())
