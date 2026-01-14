# 🖼️ Marcos y Fondos - Instrucciones

## 📁 Ubicación de archivos

Todos los archivos de marcos y fondos deben colocarse en:
```
app/src/main/res/drawable/
```

---

## 🖼️ Marcos Decorativos (Frames)

Los marcos son imágenes **PNG con transparencia** que se superponen sobre la foto.

### Archivos necesarios:

1. **frame_classic.png** - Marco clásico elegante
2. **frame_party.png** - Marco festivo con confeti
3. **frame_hearts.png** - Marco con corazones
4. **frame_stars.png** - Marco con estrellas

### Especificaciones técnicas:

- **Formato**: PNG con canal alpha (transparencia)
- **Dimensiones recomendadas**: 1920x1080 o mayor
- **Transparencia**: El centro debe ser transparente para ver la foto
- **Decoraciones**: Solo en los bordes (arriba, abajo, izquierda, derecha)
- **Peso**: < 1MB por marco

### Ejemplo de estructura:

```
┌─────────────────────────┐
│   ★  DECORACIÓN  ★      │  ← Borde superior decorado
├─────────────────────────┤
│                         │
│     (TRANSPARENTE)      │  ← Centro transparente (se ve la foto)
│                         │
├─────────────────────────┤
│   ♥  DECORACIÓN  ♥      │  ← Borde inferior decorado
└─────────────────────────┘
```

---

## 🌄 Fondos Virtuales (Backgrounds)

Los fondos son imágenes **JPG o PNG** que reemplazan el fondo original de la foto.

### Archivos necesarios:

1. **bg_city.jpg** - Ciudad (skyline, edificios)
2. **bg_beach.jpg** - Playa (arena, mar, cielo)
3. **bg_forest.jpg** - Bosque (árboles, naturaleza)
4. **bg_abstract.jpg** - Abstracto (colores, patrones)

### Especificaciones técnicas:

- **Formato**: JPG o PNG
- **Dimensiones recomendadas**: 1920x1080 o mayor
- **Orientación**: Vertical (portrait) preferiblemente
- **Calidad**: Alta resolución
- **Peso**: < 2MB por fondo

### Recomendaciones:

- Evitar fondos muy detallados o con muchos elementos pequeños
- Usar colores que contrasten bien con personas
- Fondos con profundidad de campo (desenfoque) funcionan mejor
- Evitar fondos muy oscuros o muy claros

---

## 🎨 Cómo crear marcos personalizados

### Opción 1: Con software de diseño

**Photoshop / GIMP:**
1. Crear documento 1920x1080 px
2. Fondo transparente
3. Añadir decoraciones en los bordes
4. Exportar como PNG con transparencia

**Canva / Figma:**
1. Plantilla 1920x1080 px
2. Añadir formas, textos, stickers en bordes
3. Exportar como PNG con fondo transparente

### Opción 2: Plantillas online

- **FreePik**: Buscar "photo frame PNG"
- **PNGTree**: Buscar "decorative border transparent"
- **Flaticon**: Combinar iconos para crear marco

---

## 🔍 Cómo añadir más marcos/fondos

### 1. Añadir el archivo

Coloca el archivo en `app/src/main/res/drawable/`:
```
frame_navidad.png
bg_espacio.jpg
```

### 2. Actualizar PhotoConfigActivity.kt

```kotlin
private val frameOptions = arrayOf(
    "🚫 Sin marco",
    "🖼️ Marco Clásico",
    "🎉 Marco Fiesta",
    "❤️ Marco Corazones",
    "🌟 Marco Estrellas",
    "🎄 Marco Navidad"  // ← NUEVO
)

private val frameModes = arrayOf(
    "none",
    "frame_classic",
    "frame_party",
    "frame_hearts",
    "frame_stars",
    "frame_navidad"  // ← NUEVO (sin extensión)
)
```

### 3. Recompilar la app

```bash
./gradlew assembleDebug
```

---

## 📦 Recursos gratuitos recomendados

### Marcos:
- **FreePik**: https://www.freepik.com/search?format=search&query=photo%20frame%20png
- **PNGTree**: https://pngtree.com/free-png/frame
- **Vecteezy**: https://www.vecteezy.com/png/photo-frame

### Fondos:
- **Unsplash**: https://unsplash.com (alta calidad, uso comercial gratis)
- **Pexels**: https://www.pexels.com
- **Pixabay**: https://pixabay.com

---

## 🚀 Proceso de aplicación

### Orden de efectos:
1. **Filtro** (B&W, Sepia, etc.)
2. **Eliminar fondo** (si está activado)
3. **Fondo virtual** (si hay uno seleccionado)
4. **Marco decorativo** (se superpone al final)

### Ejemplo:
```
Foto original 
→ Aplicar filtro Sepia
→ Eliminar fondo con ML Kit
→ Poner fondo de playa
→ Añadir marco de corazones
= Foto final
```

---

## ⚙️ Configuración técnica

### ML Kit - Eliminación de fondo

La app usa **Google ML Kit Selfie Segmentation** para detectar y separar personas del fondo.

**Características:**
- Detección automática de personas
- Funciona sin conexión (on-device)
- Precisión alta en retratos
- Rápido (~1-2 segundos)

**Limitaciones:**
- Funciona mejor con 1-2 personas
- Requiere buena iluminación
- Puede tener errores en bordes complejos (pelo rizado, etc.)

### Rendimiento

- **Foto simple**: ~0.5 segundos
- **Foto con filtro**: ~0.8 segundos
- **Foto con marco**: ~1 segundo
- **Foto con fondo eliminado**: ~2-3 segundos

**Nota**: El modo Photo Booth (4 fotos) NO usa eliminación de fondo por rendimiento.

---

## 🐛 Solución de problemas

### El marco no aparece
- Verifica que el archivo esté en `res/drawable/`
- Verifica que el nombre sea exacto (frame_classic.png)
- Verifica que sea PNG con transparencia
- Recompila la app

### El fondo no se aplica
- Verifica que "Eliminar fondo" esté activado
- Verifica que el archivo esté en `res/drawable/`
- Verifica las dimensiones (mínimo 1280x720)
- Revisa los logs: `adb logcat | grep CameraActivity`

### La eliminación de fondo es lenta
- Es normal, ML Kit tarda 2-3 segundos
- Usa fotos con buena iluminación
- Evita fondos muy complejos

### Error "Frame not found"
```
W/CameraActivity: Frame not found: frame_party
```
Solución: Añade el archivo `frame_party.png` en `res/drawable/`

---

## 📝 Checklist antes de publicar

- [ ] Todos los marcos en `res/drawable/`
- [ ] Todos los fondos en `res/drawable/`
- [ ] Nombres coinciden con el código
- [ ] Formatos correctos (PNG para marcos, JPG/PNG para fondos)
- [ ] Tamaños optimizados (< 1MB marcos, < 2MB fondos)
- [ ] Probado en dispositivo real
- [ ] Probado con/sin eliminación de fondo
- [ ] Probado modo Photo Booth

---

## 🎯 Próximas mejoras sugeridas

1. **Preview en tiempo real**: Mostrar marco/fondo antes de capturar
2. **Más opciones de segmentación**: No solo personas (objetos, mascotas)
3. **Ajustes de borde**: Suavizar o enduреcer bordes después de eliminar fondo
4. **Efectos de desenfoque**: Blur del fondo en lugar de reemplazarlo
5. **Marcos animados**: GIF o video loops como marcos
6. **Editor post-captura**: Ajustar posición, escala del fondo/marco

---

¡Listo! Ahora solo necesitas añadir tus archivos de marcos y fondos en `res/drawable/`. 🎉
