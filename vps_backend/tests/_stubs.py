"""Importa manolo_ari sin red ni dependencias del VPS.

En local no suelen estar httpx, websockets, groq, edge_tts ni dotenv: se
sustituyen por módulos vacíos solo si no se pueden importar. Los tests
parchean después las funciones concretas que usan.
"""
import os
import sys
import types
import warnings
from unittest.mock import MagicMock

warnings.filterwarnings('ignore', category=DeprecationWarning)  # audioop

for nombre in ('httpx', 'websockets', 'edge_tts', 'groq', 'dotenv'):
    try:
        __import__(nombre)
    except ImportError:
        modulo = types.ModuleType(nombre)
        modulo.__getattr__ = lambda attr: MagicMock()
        sys.modules[nombre] = modulo

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

import manolo_ari  # noqa: E402
