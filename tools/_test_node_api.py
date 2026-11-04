import json, time, urllib.request

BASE = 'http://127.0.0.1:6767'

def req(method, path, body=None, key=None):
    headers = {'Content-Type': 'application/json'}
    if key:
        headers['Fent-Key'] = key
    r = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None, method=method, headers=headers)
    try:
        with urllib.request.urlopen(r, timeout=10) as resp:
            return resp.status, json.loads(resp.read().decode() or '{}')
    except Exception as e:
        return 0, {'error': str(e)}

ok = True
def check(name, cond, detail=''):
    global ok
    print(('PASS  ' if cond else 'FAIL  ') + name + (' ' + str(detail) if not cond else ''))
    if not cond:
        ok = False

st, body = req('GET', '/api/health')
check('health 200', st == 200, (st, body))
check('health ok', body.get('ok') is True, body)
check('health service', body.get('service') == 'fent-api', body)

st, body = req('PUT', '/api/cosmetics/alice', {'name': 'alice', 'items': {'cape': 'FENT01', 'wings': 'FENT02'}}, key='BYQ9NUhFY6PD4E5dW6al8ngyXMlmiQ09')
check('put cosmetic 200', st == 200, (st, body))

st, body = req('GET', '/api/cosmetics')
check('get cosmetics users', st == 200 and 'users' in body, (st, body))
check('get cosmetics alice', 'alice' in (body.get('users') or {}), body)

st, body = req('PUT', '/api/emotes/alice', {'name': 'alice', 'emote': 'wave', 'at': int(time.time()*1000), 'infinite': False}, key='BYQ9NUhFY6PD4E5dW6al8ngyXMlmiQ09')
check('put emote 200', st == 200, (st, body))

st, body = req('GET', '/api/emotes')
check('get emotes', st == 200 and 'emotes' in body, (st, body))

st, body = req('DELETE', '/api/cosmetics/bob', key='BYQ9NUhFY6PD4E5dW6al8ngyXMlmiQ09')
check('delete cosmetic 200', st == 200, (st, body))

print('ALL_OK' if ok else 'SOME_FAILED')
