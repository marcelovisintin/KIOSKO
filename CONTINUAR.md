# CONTINUAR - Estado del desarrollo Kiosko Android

## 1) Objetivo del proyecto

Construir una app kiosko para tablets Android empresariales que:

- Bloquee el dispositivo en modo kiosko.
- Solo permita ejecutar aplicaciones definidas en una allowlist.
- Soporte administración corporativa (Android Enterprise / Device Owner).

## 2) Investigación usada como base

Se analizó el documento:

- `Aplicación tipo kiosco para tablets Android empresariales.pdf`

Conclusiones clave aplicadas:

- Enfoque recomendado: `fully managed / dedicated device` + `Lock task mode`.
- Uso de `DevicePolicyManager` para:
  - `setLockTaskPackages()` (allowlist real).
  - `setLockTaskFeatures()` (control de UI del sistema).
  - Restricciones de hardening (ej. overlays y fuentes desconocidas).
- Provisión sugerida:
  - Producción: Zero-touch / QR con EMM.
  - Piloto/lab: ADB + `dpm set-device-owner`.
- Soporte AppConfig para parámetros remotos.

## 3) Qué se implementó en este chat

Se creó un proyecto Android Kotlin desde cero en `d:\SCHNEIDER\2026\KIOSKO`.

### Estructura base

- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle.properties`
- `app/build.gradle.kts`
- `app/proguard-rules.pro`

### App kiosko y políticas

- Manifest con launcher HOME + admin + boot:
  - `app/src/main/AndroidManifest.xml`
- Device admin receiver:
  - `app/src/main/java/com/schneider/kiosko/KioskDeviceAdminReceiver.kt`
- Control de políticas kiosko (lock task + hardening):
  - `app/src/main/java/com/schneider/kiosko/KioskPolicyController.kt`
- Arranque automático tras reinicio:
  - `app/src/main/java/com/schneider/kiosko/BootCompletedReceiver.kt`

### UI y experiencia kiosko

- Pantalla principal launcher kiosko:
  - `app/src/main/java/com/schneider/kiosko/MainActivity.kt`
- Listado de apps permitidas:
  - `app/src/main/java/com/schneider/kiosko/AllowedApp.kt`
  - `app/src/main/java/com/schneider/kiosko/AllowedAppsAdapter.kt`
- Layouts:
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/res/layout/item_allowed_app.xml`

### Configuración administrada (EMM / AppConfig)

- Definición de managed configurations:
  - `app/src/main/res/xml/managed_configurations.xml`
- Provider para leer AppConfig y unir con defaults:
  - `app/src/main/java/com/schneider/kiosko/KioskConfigProvider.kt`
- Defaults de paquetes permitidos:
  - `app/src/main/res/values/arrays.xml`
  - `app/src/main/res/values/strings.xml`

### Recursos visuales

- Tema, colores y strings:
  - `app/src/main/res/values/themes.xml`
  - `app/src/main/res/values/colors.xml`
  - `app/src/main/res/values/strings.xml`

### Documentación inicial

- `README.md` con:
  - Arquitectura.
  - Provisión ADB.
  - Configuración AppConfig.
  - Notas operativas.

## 4) Comportamiento implementado (actual)

- Si la app es `device owner`, aplica políticas y allowlist.
- Define app kiosko como HOME persistente (`addPersistentPreferredActivity`).
- Intenta entrar en `startLockTask()` automáticamente (si no está en modo admin).
- Muestra y lanza solo apps incluidas en allowlist y presentes en el dispositivo.
- Aplica hardening:
  - `DISALLOW_CREATE_WINDOWS`
  - `DISALLOW_INSTALL_UNKNOWN_SOURCES`
  - `DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY`
  - `DISALLOW_DEBUGGING_FEATURES` (cuando no está admin mode)
  - Otras restricciones incluidas en `KioskPolicyController`.
- Reabre la app en `BOOT_COMPLETED`.

## 5) Estado actual y limitaciones detectadas

- No se ejecutó compilación en terminal porque no hay `gradle` disponible en entorno CLI.
- No se validó en dispositivo real todavía (faltan pruebas funcionales y de escape).
- El proyecto no tiene `gradlew` generado aún (se dejó indicado en README cómo generarlo).
- No se implementó aún:
  - Contenedor WebView propio multi-sitio (si se decide esa variante).
  - Integración VPN always-on.
  - Telemetría/observabilidad operativa.
  - Suite de pruebas instrumentadas.

## 6) Pasos siguientes recomendados (orden sugerido)

1. Abrir el proyecto en Android Studio.
2. Sincronizar Gradle y compilar `assembleDebug`.
3. Generar wrapper para CLI:
   - `gradle wrapper`
4. Preparar una tablet limpia (factory reset) para pruebas de device owner.
5. Instalar APK y asignar device owner:
   - `adb install app-debug.apk`
   - `adb shell dpm set-device-owner com.schneider.kiosko/.KioskDeviceAdminReceiver`
6. Verificar flujo base:
   - Entra en HOME kiosko.
   - Activa lock task.
   - Solo muestra/apps de allowlist.
7. Cargar allowlist real de negocio:
   - Por `allowed_packages_csv` en EMM/AppConfig.
   - O temporalmente en `default_allowed_packages`.
8. Ejecutar plan de pruebas de escape y resiliencia:
   - Home/overview/notificaciones.
   - Reboot.
   - App no instalada.
   - Pérdida y recuperación de red.
9. Endurecer producción:
   - Definir política de updates.
   - Integrar VPN always-on (si aplica).
   - Definir modo soporte técnico/admin.
10. Preparar despliegue masivo:
   - Zero-touch o QR provisioning con EMM.

## 7) Comandos útiles (referencia rápida)

```bash
# Build (si hay wrapper)
./gradlew assembleDebug

# Instalar APK
adb install app-debug.apk

# Asignar device owner (solo en dispositivo limpio)
adb shell dpm set-device-owner com.schneider.kiosko/.KioskDeviceAdminReceiver
```

## 8) Archivo clave para continuar el código

- Lógica de políticas: `app/src/main/java/com/schneider/kiosko/KioskPolicyController.kt`
- Orquestación UI + lock task: `app/src/main/java/com/schneider/kiosko/MainActivity.kt`
- Configuración EMM: `app/src/main/res/xml/managed_configurations.xml`

## 9) Resumen corto del avance

Quedó implementada una base funcional de kiosko enterprise Android con Device Owner, Lock Task, allowlist de apps, hardening inicial, arranque tras reboot y soporte AppConfig; falta validación en hardware real y cierre de capa operativa de producción.
