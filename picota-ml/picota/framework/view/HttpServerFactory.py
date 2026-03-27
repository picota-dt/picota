from __future__ import annotations

from http.server import ThreadingHTTPServer

from picota.framework.view.PicotaApiController import PicotaApiController
from picota.framework.view.RuntimeTrainerRequestHandler import RuntimeTrainerRequestHandler


class HttpServerFactory:
    @staticmethod
    def create(
            host: str,
            port: int,
            controller: PicotaApiController,
            log_api_requests: bool = False,
    ) -> ThreadingHTTPServer:
        RuntimeTrainerRequestHandler.controller = controller
        RuntimeTrainerRequestHandler.log_api_requests = bool(log_api_requests)
        return ThreadingHTTPServer((host, int(port)), RuntimeTrainerRequestHandler)
