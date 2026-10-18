# Rama `claude/modo1-optimized-01KAWsdwPsM88H55dEWN8Xqf` — qué se puede rescatar

**No está mergeada:** 17 commits que nunca llegaron a `dev`, con trabajo aprovechable
(verificado que no existe en `dev`). Rescatar con cherry-pick, commit a commit, en una
rama nueva desde `dev`. **Nunca `git merge`**: toca `CallStateReceiver`, la UI de
Answer+Hangup y borra ficheros vivos.

| Feature rescatable                                           | Commits              |
| ------------------------------------------------------------ | -------------------- |
| Lookup del número en webs de spam con Chrome Custom Tabs     | `f095802`, `804edd5` |
| Detección local de spam para España (regulación 2025)        | `f697767`            |
| Score y categoría de spam en historial y notificaciones      | `2bef944`, `b91e23f` |
| Almacenamiento dual al añadir a blacklist desde el historial | `40491d1`            |

**No traer:**

- `86d0c32`: dice que la accesibilidad no es necesaria. Falso, ver lección 2 en `.claude/rules/android-calls.md`.
- `79ece8a` y `f9a5d9a`: quitan los Modos 2/3, que hoy son producción.
- `aec0dd2`: borra `LogsService.ts`, que en `dev` sigue en uso.

El score de reputación (`2bef944`, `b91e23f`, `f697767`) está entre las ideas descartadas
por riesgo: ver `docs/ideas-descartadas.md`.
