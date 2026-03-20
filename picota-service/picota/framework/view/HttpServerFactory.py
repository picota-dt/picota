from __future__ import annotations

from http.server import ThreadingHTTPServer

from picota.framework.view.RuntimeTrainerRequestHandler import RuntimeTrainerRequestHandler
from picota.framework.view.TrainingApiController import TrainingApiController


class HttpServerFactory:
    @staticmethod
    def create(host: str, port: int, controller: TrainingApiController) -> ThreadingHTTPServer:
        RuntimeTrainerRequestHandler.controller = controller
        return ThreadingHTTPServer((host, int(port)), RuntimeTrainerRequestHandler)
