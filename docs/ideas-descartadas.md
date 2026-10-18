# Ideas valoradas y descartadas por riesgo (oct. 2026)

Descartadas bajo el criterio "solo cambios que no puedan romper lo que ya funciona".
Siguen siendo buenas ideas; lo que las frena es el riesgo, no el valor.

- **Whitelist por número + toggle de números ocultos.** *Riesgo: medio.* Es el agujero real de la app: `shouldShowNotification()` bloquea todos los números ocultos sin excepción y no hay whitelist por número, solo contactos — una llamada legítima con número oculto se contesta y se cuelga sin remedio. Toca el núcleo de decisión de spam, así que exige prueba en dispositivo con número oculto real. **El de más valor de los descartados.**
- **Bloquear desde la notificación.** *Riesgo: bajo-medio.* La notificación ya tiene `addAction`, así que es extender un patrón; el riesgo es el `PendingIntent` y el rebuild de `/android/`. El botón del historial ya cubre el caso principal.
- **Score de reputación** (`2bef944`, `b91e23f`, `f697767` de `claude/modo1-optimized`). *Riesgo: **alto**.* Los tres apilan cambios sobre `CallScreeningServiceImpl.java`, el fichero más caliente, con diez meses de divergencia. Es el único punto donde se puede romper la detección de spam. Si se quiere algo: coger solo `SpamPatternDetector.java` (fichero nuevo, 214 líneas, entra sin conflicto) y decidir la integración aparte.
- **Tests de la capa de llamadas.** *Riesgo: nulo; coste alto.* Ver la deuda de cobertura en la sección 9 de `CLAUDE.md`.

"Degradar a Modo 2 si cae Deepgram o Groq" estaba aquí y se hizo el 18/10/2026: ver `HISTORIAL.md`.
