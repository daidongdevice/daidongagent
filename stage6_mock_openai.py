#!/usr/bin/env python3
from http.server import BaseHTTPRequestHandler, HTTPServer
import json, time

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.endswith('/models'):
            self.send_json({"object":"list","data":[{"id":"stage6-local-test","object":"model"}]})
        else:
            self.send_json({"ok": True})

    def do_POST(self):
        length = int(self.headers.get('content-length') or 0)
        raw = self.rfile.read(length).decode('utf-8', 'replace')
        try:
            req = json.loads(raw)
        except Exception:
            req = {}
        text = ''
        for m in req.get('messages', []):
            c = m.get('content', '')
            if isinstance(c, str):
                text += '\n' + c
            elif isinstance(c, list):
                text += '\n' + ' '.join(str(x.get('text','')) for x in c if isinstance(x, dict))
        if (('Mã bí mật tôi vừa nói' in text) or ('What code did I say' in text)) and (('XANH-42' in text) or ('BLUE42' in text)):
            answer = 'XANH-42' if 'XANH-42' in text else 'BLUE42'
        elif ('Ghi nhớ mã bí mật' in text) or ('Remember code' in text):
            answer = 'OK'
        elif 'Reply with STAGE6 only' in text:
            answer = 'STAGE6'
        else:
            answer = 'OK-STAGE6'
        if req.get('stream'):
            self.send_response(200)
            self.send_header('Content-Type', 'text/event-stream')
            self.send_header('Cache-Control', 'no-cache')
            self.end_headers()
            chunk = {"id":"chatcmpl-stage6","object":"chat.completion.chunk","created":0,"model":req.get('model','stage6-local-test'),"choices":[{"index":0,"delta":{"content":answer},"finish_reason":None}]}
            self.wfile.write(('data: '+json.dumps(chunk)+'\n\n').encode()); self.wfile.flush()
            done = {"id":"chatcmpl-stage6","object":"chat.completion.chunk","created":0,"model":req.get('model','stage6-local-test'),"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}
            self.wfile.write(('data: '+json.dumps(done)+'\n\n').encode()); self.wfile.write(b'data: [DONE]\n\n'); self.wfile.flush()
            return
        body = {
            "id": "chatcmpl-stage6",
            "object": "chat.completion",
            "created": 0,
            "model": req.get('model', 'stage6-local-test'),
            "choices": [{"index": 0, "message": {"role": "assistant", "content": answer}, "finish_reason": "stop"}],
            "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2},
        }
        self.send_json(body)

    def send_json(self, body):
        data = json.dumps(body).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt, *args):
        pass

HTTPServer(('127.0.0.1', 20129), Handler).serve_forever()
