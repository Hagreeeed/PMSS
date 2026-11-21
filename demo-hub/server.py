"""Lab 4 REST mock hub. No packages, credentials or external cloud account needed.
Run: python demo-hub/server.py; open http://127.0.0.1:8787 in the PC browser.
Android emulator: adb reverse tcp:8787 tcp:8787, REMOTE > Demo hub.
"""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Lock
import json

state = {'id': 1, 'completed': False}
lock = Lock()
PAGE = '''<!doctype html><html lang="uk"><meta charset="utf-8"><title>Light Smart Hub — Lab 4</title>
<style>body{font:20px system-ui;max-width:760px;margin:60px auto;padding:24px;background:#f5f6fa;color:#17223b}button{font:inherit;padding:16px 36px;margin:12px;border-radius:10px;border:1px solid #aab}pre{background:white;padding:24px;border-radius:12px}#status{font-size:32px}</style>
<h1>Light Smart Hub · ЛР4</h1><p>Зовнішня консоль демонстраційного REST-хаба</p>
<p id="status"></p><button onclick="send(true)">Лампа ON</button><button onclick="send(false)">Лампа OFF</button>
<pre id="json"></pre><p>GET /command → Android Retrofit → актуатор<br>Опитування: 3 с після завершення попереднього запиту.</p>
<script>async function refresh(){let r=await fetch('/command');let x=await r.json();document.querySelector('#status').textContent='Команда: '+(x.completed?'ON':'OFF');document.querySelector('#json').textContent=JSON.stringify(x,null,2)}
async function send(completed){await fetch('/command',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({completed})});refresh()}refresh();setInterval(refresh,2000)</script></html>'''

class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'
    def respond(self, code, body, kind='application/json'):
        data = body.encode('utf-8')
        self.send_response(code); self.send_header('Content-Type',kind+'; charset=utf-8')
        self.send_header('Cache-Control','no-store'); self.send_header('Content-Length',str(len(data)))
        self.end_headers(); self.wfile.write(data)
    def do_GET(self):
        if self.path == '/': return self.respond(200, PAGE, 'text/html')
        if self.path == '/command':
            with lock: body = json.dumps(state)
            return self.respond(200, body)
        self.respond(404, '{}')
    def do_POST(self):
        if self.path != '/command': return self.respond(404,'{}')
        try:
            length = int(self.headers.get('Content-Length','0'))
            if length < 1 or length > 1024: raise ValueError()
            data = json.loads(self.rfile.read(length))
            if not isinstance(data, dict) or type(data.get('completed')) is not bool: raise ValueError()
            with lock:
                state['id'] += 1; state['completed'] = data['completed']; body = json.dumps(state)
            self.respond(200,body)
        except (ValueError, TypeError): self.respond(400,'{"error":"completed must be boolean"}')

if __name__ == '__main__':
    print('Demo hub: http://127.0.0.1:8787 (Ctrl+C to stop)', flush=True)
    ThreadingHTTPServer(('127.0.0.1',8787),Handler).serve_forever()
