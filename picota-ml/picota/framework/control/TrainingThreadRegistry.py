from __future__ import annotations

import logging
import threading
import time
from collections.abc import Callable
from typing import Any

from picota.framework.control.ticket.TicketStore import TicketStore

logger = logging.getLogger(__name__)


class TrainingThreadRegistry:
    def __init__(self):
        self._threads: dict[str, threading.Thread] = {}
        self._threads_lock = threading.Lock()

    def start(self, *, ticket_id: str, target: Callable[..., Any], args: tuple[Any, ...]) -> None:
        thread = threading.Thread(
            target=target,
            args=args,
            daemon=True,
            name=f"training-{ticket_id[:8]}",
        )
        with self._threads_lock:
            self._threads[ticket_id] = thread
        thread.start()
        logger.info("Training thread launched (ticket_id=%s, thread=%s)", ticket_id, thread.name)

    def remove(self, ticket_id: str) -> None:
        with self._threads_lock:
            self._threads.pop(ticket_id, None)
        logger.info("Training thread removed from registry (ticket_id=%s)", ticket_id)

    def wait_terminal(
            self,
            *,
            ticket_store: TicketStore,
            ticket_id: str,
            timeout_sec: float = 60.0,
            poll_sec: float = 0.1,
    ) -> dict[str, Any] | None:
        deadline = time.monotonic() + timeout_sec
        while time.monotonic() < deadline:
            record = ticket_store.get(ticket_id)
            if record is None:
                return None
            status = str(record.get("status"))
            if status in {TicketStore.STATUS_COMPLETED, TicketStore.STATUS_FAILED}:
                return record
            time.sleep(poll_sec)
        return ticket_store.get(ticket_id)
