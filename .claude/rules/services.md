---
paths: ["src/services/**"]
---

# Servicios de la app (src/services/) — trampas

- **`BackendSyncService.ts`** — cliente HTTP de `control_api.py`: `syncMode()` y `getCallLog()`, con `X-API-Key` en ambas. Timeout de 5 s y degradación silenciosa (devuelve `[]`, no lanza) para que la app siga usable sin VPS; un 401 sí se distingue en el log
- ⚠️ **`ContactService.ts`** y **`ContactsService.ts`** — dos ficheros distintos, con nombres casi idénticos, **ambos en uso**: `DashboardScreen` importa `contactsService` de `ContactService.ts`, `WhitelistScreen` importa `ContactsService` de `ContactsService.ts`. De ahí el bug del Modo Radical (ver sección 9 del `CLAUDE.md`)
