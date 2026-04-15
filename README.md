# Kiosko Android (Dedicated Device)

App Android de tipo kiosko para tablets empresariales.

## Version actual

- `versionCode`: `2`
- `versionName`: `1.0.1-produccion-siempre-andando`
- Fecha de release: `2026-04-15`

## Que incluye esta version

- Lock task con `device owner` y launcher HOME persistente.
- Login con `usuario + PIN` para sesion `USER` y `ADMIN`.
- Panel admin local para gestion de usuarios, permisos y accesos web.
- Accesos web dentro de `KioskWebActivity` (WebView interno).
- Modo inmersivo en launcher y WebView.
- Ajuste de insets en panel admin para que `Salir de kiosko` quede tocable aun con barra de navegacion visible.
- Fallback para OEMs que ocultan apps launchables en queries de launcher (caso Xiaomi/MIUI).

## Arquitectura

- `MainActivity`: login, launcher kiosko, panel admin, insets seguros para controles inferiores.
- `KioskWebActivity`: contenedor web interno, navegacion de retorno a HOME kiosko y controles locales.
- `KioskDeviceAdminReceiver`: receiver de administracion del dispositivo.
- `KioskPolicyController`: aplica lock task y hardening.
- `KioskConfigProvider`: lee allowlist desde AppConfig y defaults locales.
- `BootCompletedReceiver`: relanza kiosko tras reinicio.
- `KioskUserStore`: persistencia local de usuarios y permisos.
- `KioskWebLinkStore`: persistencia local de accesos web.

## Configuracion de apps permitidas

La lista de paquetes permitidos se combina desde:

1. `app/src/main/res/values/arrays.xml` (`default_allowed_packages`)
2. Managed config `allowed_packages_csv` (separado por comas)
3. El propio paquete de la app kiosko (se agrega siempre)

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

Nota Xiaomi/MIUI: si aparece `INSTALL_FAILED_USER_RESTRICTED`, la instalacion por ADB fue bloqueada por politica del dispositivo y se debe instalar manualmente desde la tablet.

## Build

Proyecto Android con Kotlin + ViewBinding.

- `minSdk`: 26 (Android 8.0)
- `targetSdk`: 35

Compilar desde Android Studio (recomendado).
Tambien se puede compilar por CLI:

```bash
./gradlew assembleDebug
# Windows PowerShell / CMD
.\gradlew.bat assembleDebug
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

La app valida URLs `http/https`.

## Notas operativas

- Si la app no esta en `device owner`, no puede aplicar politicas globales.
- `Lock task` solo es fuerte cuando el paquete esta allowlisteado por DPC.
- Para produccion se recomienda enrolamiento Android Enterprise (zero-touch o QR) con EMM.

## Notas de configuracion de tablet

- En `Sistema > Avanzado > Informacion de la tablet > Numero de compilacion`, tocar 7 veces para habilitar opciones de desarrollador.
- En `Opciones de desarrollador`, activar `Depuracion USB`.
- En `Opciones de desarrollador`, configurar `USB predeterminado` en `Transferencia de archivos`.
- Si la tablet no aparece bien en la PC, quitarla en `Administrador de dispositivos` y ejecutar `Buscar cambios de hardware`.
