"""Локальний REST-хаб для Light Exposure Tracker (ЛР4, ЛР7).

GET  /command   команда для лампи в режимі REMOTE
POST /command   змінити команду з браузера
POST /readings  прийняти запис із застосунку, зберегти в readings.json
GET  /readings  останні отримані записи
GET  /          консоль у браузері

Запуск: python demo-hub/server.py, потім adb reverse tcp:8787 tcp:8787.
"""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Lock
import json

STORE = Path(__file__).with_name('readings.json')
REQUIRED = {'sampleId': str, 'deviceId': str, 'timestamp': int, 'type': str}

state = {'id': 1, 'completed': False}
readings = []
lock = Lock()

PAGE = '''<!doctype html><html lang="uk"><meta charset="utf-8"><title>Light Smart Hub</title>
<style>body{font:16px system-ui;max-width:1000px;margin:30px auto;padding:0 16px;background:#f5f6fa;color:#17223b}
button{font:inherit;padding:10px 24px;margin:6px;border-radius:8px;border:1px solid #aab}
table{border-collapse:collapse;width:100%;background:white}td,th{border:1px solid #dde;padding:6px 8px;text-align:left}
#status{font-size:24px}</style>
<h1>Light Smart Hub</h1>
<p id="status"></p><button onclick="send(true)">Лампа ON</button><button onclick="send(false)">Лампа OFF</button>
<h2>Отримані записи (<span id="count">0</span>)</h2>
<table><thead><tr><th>#</th><th>Час</th><th>Освітленість, лк</th><th>Сирий, лк</th><th>Стан моделі</th><th>Подія</th></tr></thead><tbody id="rows"></tbody></table>
<script>
async function refresh(){
 let c=await (await fetch('/command')).json();
 document.querySelector('#status').textContent='Команда лампи: '+(c.completed?'ON':'OFF')+' (#'+c.id+')';
 let r=await (await fetch('/readings')).json();
 document.querySelector('#count').textContent=r.total;
 document.querySelector('#rows').innerHTML=r.items.map(x=>'<tr><td>'+x.id+'</td><td>'+new Date(x.timestamp).toLocaleTimeString('uk-UA')+
  '</td><td>'+(x.value??'—')+'</td><td>'+(x.rawValue??'—')+'</td><td>'+(x.state??'—')+(x.confidence?' '+Math.round(x.confidence*100)+'%':'')+
  '</td><td>'+(x.event??'')+'</td></tr>').join('');
}
async function send(completed){await fetch('/command',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({completed})});refresh()}
refresh();setInterval(refresh,2000)</script></html>'''


def load():
    if STORE.exists():
        readings.extend(json.loads(STORE.read_text(encoding='utf-8')))


class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'

    def respond(self, code, body, kind='application/json'):
        data = body.encode('utf-8')
        self.send_response(code)
        self.send_header('Content-Type', kind + '; charset=utf-8')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def read_json(self):
        length = int(self.headers.get('Content-Length', '0'))
        if length < 1 or length > 4096:
            raise ValueError()
        return json.loads(self.rfile.read(length))

    def do_GET(self):
        if self.path == '/':
            return self.respond(200, PAGE, 'text/html')
        if self.path == '/command':
            with lock:
                return self.respond(200, json.dumps(state))
        if self.path == '/readings':
            with lock:
                body = {'total': len(readings), 'items': readings[-30:][::-1]}
            return self.respond(200, json.dumps(body, ensure_ascii=False))
        self.respond(404, '{}')

    def do_POST(self):
        try:
            data = self.read_json()
            if not isinstance(data, dict):
                raise ValueError()
            if self.path == '/command':
                if type(data.get('completed')) is not bool:
                    raise ValueError()
                with lock:
                    state['id'] += 1
                    state['completed'] = data['completed']
                    return self.respond(200, json.dumps(state))
            if self.path == '/readings':
                if any(type(data.get(k)) is not t for k, t in REQUIRED.items()):
                    raise ValueError()
                with lock:
                    # повторна відправка того самого sampleId не створює дубль
                    existing = next((r for r in readings if r['sampleId'] == data['sampleId']), None)
                    if existing is None:
                        existing = dict(data, id=len(readings) + 1)
                        readings.append(existing)
                        STORE.write_text(json.dumps(readings, ensure_ascii=False), encoding='utf-8')
                    ack = {'id': existing['id'], 'sampleId': existing['sampleId']}
                print(f"POST /readings #{ack['id']} {data.get('value')} lx {data.get('event') or data.get('state')}", flush=True)
                return self.respond(201, json.dumps(ack))
            self.respond(404, '{}')
        except (ValueError, TypeError):
            self.respond(400, '{"error":"bad request"}')

    def log_message(self, *args):
        pass


if __name__ == '__main__':
    load()
    print('Hub: http://127.0.0.1:8787 (Ctrl+C to stop)', flush=True)
    ThreadingHTTPServer(('127.0.0.1', 8787), Handler).serve_forever()
