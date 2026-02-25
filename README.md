# Kiosko Android (Dedicated Device)

App Android de tipo kiosco para tablets empresariales. Implementa:

- `Device owner` (DPC minimo en la misma app).
- `Lock task mode` para bloquear salida del entorno.
- Allowlist de paquetes permitidos.
- Bloqueo de overlays con `DISALLOW_CREATE_WINDOWS`.
- Arranque automatico despues de reinicio.
- Configuracion remota por `Managed Configurations (AppConfig)`.
- Login por `usuario + PIN` con sesion `USER` y sesion `ADMIN`.
- Panel admin local para soporte y diagnostico.
- Gestion local de usuarios con PIN y asignacion de apps por usuario.
- Gestion de accesos web (URL) con nombre visible y asignacion por usuario.
- En sesion `USER`, el encabezado muestra el nombre del usuario autenticado.

## Arquitectura

- `MainActivity`: login, launcher kiosco, panel admin y permisos por usuario.
- `KioskDeviceAdminReceiver`: receiver de administracion del dispositivo.
- `KioskPolicyController`: aplica lock task y hardening.
- `KioskConfigProvider`: lee allowlist desde AppConfig y defaults locales.
- `BootCompletedReceiver`: relanza kiosco al reiniciar.
- `KioskUserStore`: persistencia local de usuarios y permisos.
- `KioskWebLinkStore`: persistencia local de accesos web.

## Configuracion de apps permitidas

La lista de paquetes permitidos se combina desde:

1. `app/src/main/res/values/arrays.xml` (`default_allowed_packages`)
2. Managed config `allowed_packages_csv` (separado por comas)
3. El propio paquete de la app kiosco (se agrega siempre)

## Provision rapida por ADB (piloto/lab)

1. Restaurar tablet a fabrica.
2. Habilitar depuracion USB.
3. Instalar APK:

```bash
adb install app-debug.apk
```

4. Asignar device owner:

```bash
adb shell dpm set-device-owner com.schneider.kiosko/.KioskDeviceAdminReceiver
```

5. Abrir la app `Kiosko`.

## Build

Proyecto Android con Kotlin + ViewBinding.

- `minSdk`: 26 (Android 8.0)
- `targetSdk`: 35

Compilar desde Android Studio (recomendado).
Si deseas compilar por CLI, primero genera wrapper y luego arma el APK:

```bash
gradle wrapper
./gradlew assembleDebug
```

## Managed Configurations (EMM)

Definidas en `app/src/main/res/xml/managed_configurations.xml`:

- `allowed_packages_csv`
- `allow_system_ui`
- `admin_mode_enabled`
- `pin_required`
- `user_pin`
- `admin_pin`

Ejemplo:

```text
allowed_packages_csv=com.android.chrome,com.microsoft.emmx,com.tuempresa.webapp
allow_system_ui=false
admin_mode_enabled=false
pin_required=true
user_pin=1234
admin_pin=2026
```

## Credenciales admin locales (default)

- Usuario admin local: `kadmin`
- PIN admin por defecto: `2026`

Nota: si EMM envia `admin_pin`, ese valor remoto reemplaza el PIN por defecto.

## Gestion de usuarios (panel admin)

Desde sesion `ADMIN` puedes:

- Crear usuarios locales (nombre + PIN de 4 digitos).
- Eliminar usuarios (debe quedar al menos uno).
- Seleccionar un usuario y marcar/desmarcar apps instaladas permitidas para ese usuario.
- Seleccionar/desmarcar accesos web para ese usuario directamente en la seccion `Accesos web`.

El launcher en sesion `USER` solo muestra los items asignados al usuario autenticado (apps + URLs).

## Accesos web (panel admin)

Desde sesion `ADMIN` puedes:

- Cargar una URL con nombre visible (`Nombre + URL`) usando `Agregar URL`.
- Ver las URLs cargadas debajo del boton `Agregar URL`.
- Asignar/desasignar cada URL al usuario seleccionado en el spinner.

La app valida URLs `http/https` y agrega una URL de ejemplo por defecto:

- `Odoo Schneider` -> `https://schneider-srl.odoo.com/web/login?redirect=%2Fodoo%3F`

## Notas operativas

- Si la app no esta en `device owner`, no puede aplicar politicas globales.
- `Lock task` solo es fuerte cuando el paquete esta allowlisteado por DPC.
- Para produccion se recomienda enrolamiento Android Enterprise (zero-touch o QR) con EMM.

## Notas config tablet

-en sistema-avanzado-informacion tablet-numero de compilacion " picar 7 veces para entrar en modo opciones para desarrollador"

-en opciones desarrollador- activar depuracion USB
-en opciones desarrollador- configuracion de USB predeterminada- activar trasnferencia de archivos.
-tablet conectada por cable a pc. ir en pc a administrador de dispositivos eliminar la tablet y luego en la pestaña accion buscar cambios en hardware.
