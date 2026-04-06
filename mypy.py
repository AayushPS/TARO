"""Repo-local launcher shim for the mandated `python -m mypy ... -q` command."""

from __future__ import annotations

import os
import runpy
import sys

REPO_ROOT = os.path.abspath(os.path.dirname(__file__))
QUIET_FLAGS = {"-q", "--quiet"}


def main() -> None:
    sys.argv = [sys.argv[0], *[arg for arg in sys.argv[1:] if arg not in QUIET_FLAGS]]
    sys.path = [
        entry
        for entry in sys.path
        if os.path.abspath(entry or os.getcwd()) != REPO_ROOT
    ]
    runpy.run_module("mypy", run_name="__main__", alter_sys=True)


if __name__ == "__main__":
    main()
