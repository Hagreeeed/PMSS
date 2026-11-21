import http.client
import json
import threading
import unittest
from http.server import ThreadingHTTPServer
from server import Handler


class HubTest(unittest.TestCase):
    def setUp(self):
        self.hub=ThreadingHTTPServer(('127.0.0.1',0),Handler)
        self.thread=threading.Thread(target=self.hub.serve_forever,daemon=True)
        self.thread.start()
        self.client=http.client.HTTPConnection('127.0.0.1',self.hub.server_port)

    def tearDown(self):
        self.client.close();self.hub.shutdown();self.hub.server_close();self.thread.join()

    def request(self,method,body=None):
        payload=json.dumps(body) if body is not None else None
        self.client.request(method,'/command',body=payload,headers={'Content-Type':'application/json'})
        response=self.client.getresponse()
        return response.status,json.loads(response.read())

    def test_command_revisions_and_keepalive(self):
        status,first=self.request('POST',{'completed':True})
        self.assertEqual(200,status);self.assertTrue(first['completed'])
        status,second=self.request('POST',{'completed':False})
        self.assertEqual(first['id']+1,second['id'])
        for _ in range(4):
            status,last=self.request('GET')
            self.assertEqual(200,status);self.assertEqual(second,last)

    def test_invalid_values_do_not_change_command(self):
        _,before=self.request('GET')
        for value in ([],{'completed':'true'},{'completed':1},{'completed':None}):
            status,_=self.request('POST',value);self.assertEqual(400,status)
        _,after=self.request('GET');self.assertEqual(before,after)


if __name__=='__main__':unittest.main()
