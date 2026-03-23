from __future__ import annotations

from http.server import ThreadingHTTPServer

from picota.framework.view.PicotaApiController import PicotaApiController
from picota.framework.view.RuntimeTrainerRequestHandler import RuntimeTrainerRequestHandler


class HttpServerFactory:
    @staticmethod
    def create(host: str, port: int, controller: PicotaApiController) -> ThreadingHTTPServer:
        RuntimeTrainerRequestHandler.controller = controller
        return ThreadingHTTPServer((host, int(port)), RuntimeTrainerRequestHandler)
