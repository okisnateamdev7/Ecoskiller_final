import os
import http.server
import socketserver
import json

class ServiceHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.end_headers()
        
        path = self.path
        if path in ["/auth/login", "/auth/register", "/auth/mfa", "/auth/rbac", "/auth/session"]:
            module = path.split('/')[-1]
            response = {"status": "success", "service": "auth-service", "module": module, "message": f"Endpoint {path} active"}
            self.wfile.write(json.dumps(response).encode())
        else:
            self.wfile.write(b'{"status": "ok", "service": "auth-service", "message": "Root handler"}')

PORT = 8080
with socketserver.TCPServer(('', PORT), ServiceHandler) as httpd:
    print(f'auth-service starting on port {PORT}', flush=True)
    httpd.serve_forever()
