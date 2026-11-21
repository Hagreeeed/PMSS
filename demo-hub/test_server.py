import http.client
import json
import tempfile
import threading
import unittest
from http.server import ThreadingHTTPServer
from pathlib import Path

import server
from server import Handler


class HubTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        server.STORE = Path(self.tmp.name) / 'readings.json'
        server.readings.clear()
        self.hub = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        self.thread = threading.Thread(target=self.hub.serve_forever, daemon=True)
        self.thread.start()
        self.client = http.client.HTTPConnection('127.0.0.1', self.hub.server_port)

    def tearDown(self):
        self.client.close()
        self.hub.shutdown()
        self.hub.server_close()
        self.thread.join()
        self.tmp.cleanup()

    def request(self, method, path, body=None):
        payload = json.dumps(body) if body is not None else None
        self.client.request(method, path, body=payload, headers={'Content-Type': 'application/json'})
        response = self.client.getresponse()
        return response.status, json.loads(response.read())

    def test_command_revisions(self):
        _, first = self.request('POST', '/command', {'completed': True})
        _, second = self.request('POST', '/command', {'completed': False})
        self.assertEqual(first['id'] + 1, second['id'])
        self.assertEqual((200, second), self.request('GET', '/command'))

    def test_invalid_command_is_rejected(self):
        for value in ([], {'completed': 'true'}, {'completed': 1}):
            self.assertEqual(400, self.request('POST', '/command', value)[0])

    def test_reading_is_stored_once_and_acknowledged(self):
        reading = {'sampleId': 'abc', 'deviceId': 'd1', 'timestamp': 1, 'type': 'light', 'value': 420.0}
        status, ack = self.request('POST', '/readings', reading)
        self.assertEqual((201, 'abc'), (status, ack['sampleId']))
        _, again = self.request('POST', '/readings', reading)
        self.assertEqual(ack, again)
        _, listing = self.request('GET', '/readings')
        self.assertEqual(1, listing['total'])
        self.assertEqual(1, len(json.loads(server.STORE.read_text(encoding='utf-8'))))

    def test_reading_without_required_fields_is_rejected(self):
        self.assertEqual(400, self.request('POST', '/readings', {'value': 1})[0])


if __name__ == '__main__':
    unittest.main()
