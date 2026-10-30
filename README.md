# YT Music Craft (Fabric 1.21.11)

Escucha o mira YouTube / YouTube Music mientras juegas, con tu cuenta de Google:
buscador, tus listas de reproducción, videos que te gustan, cola con auto-siguiente,
HUD "ahora suena" y atajos de teclado.

## Cómo funciona
El mod levanta un servidor local (`http://localhost:48731`, solo accesible desde tu PC) que sirve
una página con el **reproductor oficial de YouTube** (IFrame API). Esa pestaña se abre en tu navegador:
ahí se ve el video, o la minimizas y solo escuchas el audio. Desde Minecraft controlas todo.
La búsqueda y tus listas usan la **YouTube Data API v3** con tu login de Google (OAuth, solo lectura).

## Controles
| Tecla (por defecto) | Acción |
|---|---|
| `M` | Abrir el menú (buscar, listas, controles) |
| Keypad `5` / `6` / `4` | Play-Pausa / Siguiente / Anterior |

Todas se pueden cambiar en *Opciones > Controles > YT Music Craft*.

## Configurar la cuenta de Google (una sola vez)
1. Entra a <https://console.cloud.google.com/> y crea un proyecto.
2. *APIs y servicios > Biblioteca* → activa **YouTube Data API v3**.
3. *Pantalla de consentimiento OAuth* → tipo **Externo** → en "Usuarios de prueba" agrega tu correo de Google.
4. *Credenciales > Crear credenciales > ID de cliente de OAuth* → tipo **Aplicación web**.
   En **Orígenes de JavaScript autorizados** agrega `http://localhost:48731`.
5. Copia el Client ID. En el juego pulsa `M` > **Conectar / Abrir**; en la página pega el Client ID,
   guarda y pulsa **Conectar con Google**. (También puedes ponerlo en `config/ytmusic.json`.)

## Compilar
```bash
gradle wrapper --gradle-version 9.4.1   # solo la primera vez (genera gradlew)
./gradlew build
```
El `.jar` queda en `build/libs/`. Requiere **Java 21** y Gradle 9.4.1. Con GitHub Actions (`.github/workflows`) se compila
solo en cada push, y al subir un tag `v1.0.0` se crea un Release con el `.jar`.

## Solo música
- La búsqueda está limitada a la categoría **Música** de YouTube. Para buscar de todo, pon `"musicOnly": false` en `config/ytmusic.json`.
- La página del reproductor es compacta (356x200). Redimensiona la ventana del navegador a ese tamaño y minimízala:
  el audio sigue sonando. YouTube exige que el reproductor incrustado sea visible (mín. 200x200), por eso no se oculta del todo.

## Límites conocidos
- La búsqueda cuesta 100 unidades de cuota; el plan gratis da 10.000/día (~100 búsquedas).
- Videos que el dueño no permite incrustar se saltan automáticamente.
- El video (si lo miras) se ve en el navegador, no dentro de Minecraft. Para verlo dentro del juego haría falta
  integrar un navegador embebido (p. ej. MCEF), que es mucho más pesado.
- El token de Google dura ~1 h y se renueva solo mientras la pestaña esté abierta.
- Si el navegador bloquea el autoplay, aparece un botón "Click para activar el audio".
