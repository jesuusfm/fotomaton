# 📸 Instrucciones para Añadir Marcos y Fondos

## ✅ Sistema Completado

El sistema de marcos con preview en tiempo real está **completamente implementado**. Los cambios incluyen:

### Archivos Creados
1. **FramePreviewActivity.kt** - Activity con cámara y preview de marcos en tiempo real
2. **ThemeAdapter.kt** - Adaptador para mostrar las temáticas (verano, navidad, etc.)
3. **FrameAdapter.kt** - Adaptador para mostrar los marcos disponibles
4. **activity_frame_preview.xml** - Layout con cámara + RecyclerViews
5. **item_theme.xml** - Layout para tarjetas de temáticas
6. **item_frame.xml** - Layout para thumbnails de marcos
7. **assets/marcos/** - Estructura de carpetas por temática
8. **assets/fondos/** - Estructura de carpetas por temática

### Archivos Modificados
1. **PhotoConfigActivity.kt**:
   - Eliminado spinner de marcos
   - Añadido botón "🖼️ Seleccionar Marco"
   - Implementado launcher para FramePreviewActivity
   - Texto de estado mostrando marco seleccionado

2. **activity_photo_config.xml**:
   - Reemplazado `spinner_frame` por `button_select_frame`
   - Añadido `frame_status` (TextView para mostrar selección)

3. **CameraActivity.kt**:
   - Añadido variable `framePath` para recibir ruta del marco
   - Actualizado `applyFrame()` para cargar desde assets en vez de drawable
   - Todas las llamadas a `applyFrame` actualizadas con `framePath`

4. **AndroidManifest.xml**:
   - Registrada FramePreviewActivity

## 📁 Estructura de Carpetas

```
app/src/main/assets/
  ├── marcos/
  │   ├── verano/         (☀️)
  │   ├── navidad/        (🎄)
  │   ├── cumpleaños/     (🎂)
  │   ├── frio/           (❄️)
  │   ├── comunion/       (✝️)
  │   ├── boda/           (💍)
  │   └── otros/          (🎨)
  │
  └── fondos/
      ├── verano/
      ├── navidad/
      ├── cumpleaños/
      ├── frio/
      ├── comunion/
      ├── boda/
      └── otros/
```

## 🖼️ Cómo Añadir Marcos

### 1. Preparar imágenes PNG
- **Formato**: PNG con transparencia (canal alpha)
- **Tamaño recomendado**: 1080x1920px o superior
- **Orientación**: Vertical (portrait)
- **Diseño**: El área central debe estar transparente para ver la foto
- **Peso**: Menos de 2MB por archivo

### 2. Nombrar archivos
```
marco_verano_flores.png
marco_navidad_nieve.png
marco_cumpleaños_globos.png
marco_frio_copos.png
marco_comunion_cruz.png
marco_boda_corazones.png
marco_otros_abstracto.png
```

### 3. Copiar a la carpeta correcta
Usando Android Studio:
1. Click derecho en `app/src/main/assets/marcos/[temática]/`
2. **Paste** tus archivos PNG
3. La app detectará automáticamente los nuevos marcos

### 4. Probar
1. Ejecuta la app
2. Ve a "Tomar Foto"
3. Click en "🖼️ Seleccionar Marco"
4. Selecciona una temática (ej: Verano ☀️)
5. Click en un marco
6. Verás el preview en tiempo real sobre la cámara
7. Click "✓ Confirmar" para seleccionar

## 🎨 Flujo de Usuario Implementado

```
PhotoConfigActivity
       ↓
    [🖼️ Seleccionar Marco]
       ↓
FramePreviewActivity
   - Cámara en vivo
   - RecyclerView temáticas (horizontal)
   - RecyclerView marcos (horizontal)
   - Preview en tiempo real
       ↓
    [✓ Confirmar]
       ↓
PhotoConfigActivity
   "✓ Marco seleccionado: Verano"
       ↓
    [Comenzar]
       ↓
CameraActivity
   - Tomar foto
   - Aplicar marco automáticamente
```

## 🎯 Características Implementadas

✅ **Detección dinámica**: La app escanea las carpetas de assets automáticamente
✅ **Preview en tiempo real**: Ves cómo queda el marco antes de tomar la foto
✅ **Organización por temáticas**: 7 categorías predefinidas
✅ **Selección visual**: RecyclerViews horizontales con thumbnails
✅ **Sin marco**: Opción para no usar marco
✅ **Confirmación**: Botón de confirmar/cancelar
✅ **Indicador visual**: Checkmark en marco seleccionado
✅ **Retroalimentación**: Texto mostrando temática seleccionada

## 🔧 Debugging

Si los marcos no aparecen:
1. Verifica que los archivos estén en `app/src/main/assets/marcos/[temática]/`
2. Asegúrate que sean archivos **.png** (no .jpg)
3. Los nombres no deben contener espacios ni caracteres especiales
4. Haz **Build > Clean Project** y **Rebuild Project**
5. Reinstala la app en el dispositivo

## 📝 Logs útiles

```kotlin
// FramePreviewActivity.kt registra:
android.util.Log.d("FramePreview", "Found ${themes.size} themes")
android.util.Log.d("FramePreview", "Found ${frames.size} frames in $themeName")
```

Puedes ver estos logs en **Logcat** para verificar que los archivos se detectan correctamente.

## 🎁 Próximos pasos

Para implementar **fondos virtuales** (similar a marcos):
1. Crear `BackgroundPreviewActivity` (similar a FramePreviewActivity)
2. Escanear `assets/fondos/[temática]/`
3. No necesita preview de cámara (se aplica después con ML Kit)
4. Retornar ruta del fondo seleccionado
5. CameraActivity ya tiene la función `applyBackgroundWithMask()` lista

## 📞 Soporte

Si necesitas:
- Añadir más temáticas: Solo crea una nueva carpeta en `assets/marcos/`
- Cambiar iconos de temáticas: Modifica `getThemeIcon()` en FramePreviewActivity.kt
- Ajustar UI: Edita los layouts en `res/layout/`

---
**Nota**: Este sistema está diseñado para que los usuarios puedan añadir marcos fácilmente sin modificar código. Solo copian archivos PNG a las carpetas de assets y la app los detecta automáticamente.
