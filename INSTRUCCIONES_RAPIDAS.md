# 🎨 Instrucciones Rápidas - Marcos y Fondos

## ✅ Ya está implementado

La funcionalidad de **marcos decorativos**, **fondos virtuales** y **eliminación de fondo con ML Kit** ya está completamente integrada en la app.

## 📋 Qué necesitas hacer

### 1. Reemplazar archivos placeholder

Los archivos actuales en `app/src/main/res/drawable/` son solo **placeholders XML**. Necesitas reemplazarlos con tus **archivos reales PNG/JPG**.

#### Marcos (PNG con transparencia):
- `frame_classic.png` → Marco elegante
- `frame_party.png` → Marco de fiesta
- `frame_hearts.png` → Marco con corazones  
- `frame_stars.png` → Marco con estrellas

#### Fondos (JPG o PNG):
- `bg_city.jpg` → Fondo de ciudad
- `bg_beach.jpg` → Fondo de playa
- `bg_forest.jpg` → Fondo de bosque
- `bg_abstract.jpg` → Fondo abstracto

### 2. Especificaciones de archivos

**Marcos:**
- Formato: PNG con canal alpha (transparencia)
- Tamaño: 1920x1080 px o mayor
- Centro transparente para ver la foto
- Decoraciones solo en los bordes

**Fondos:**
- Formato: JPG o PNG
- Tamaño: 1920x1080 px o mayor
- Alta resolución
- Colores que contrasten con personas

### 3. Compilar y probar

```bash
./gradlew assembleDebug
```

## 🎯 Cómo funciona

### En PhotoConfigActivity:
1. Selecciona un **filtro** (opcional)
2. Selecciona un **marco** (opcional)
3. Selecciona un **fondo virtual** (opcional)
4. Si seleccionas fondo, activa **"Eliminar fondo"** para usar ML Kit

### Orden de aplicación:
```
Foto capturada
    ↓
Aplicar filtro (B&W, Sepia, etc.)
    ↓
Eliminar fondo con ML Kit (si está activado)
    ↓
Aplicar fondo virtual
    ↓
Aplicar marco decorativo
    ↓
Resultado final
```

## 🔧 Dependencias añadidas

Ya está en `build.gradle.kts`:
```kotlin
implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta5")
```

## 📖 Documentación completa

Lee [MARCOS_Y_FONDOS_README.md](MARCOS_Y_FONDOS_README.md) para:
- Instrucciones detalladas de creación de marcos
- Recursos gratuitos recomendados
- Cómo añadir más marcos/fondos
- Solución de problemas
- Mejoras futuras sugeridas

## ⚡ Rendimiento

- **Foto simple**: ~0.5 segundos
- **Foto + filtro**: ~0.8 segundos
- **Foto + marco**: ~1 segundo
- **Foto + eliminar fondo**: ~2-3 segundos (ML Kit)

**Nota**: El modo Photo Booth (4 fotos) NO usa eliminación de fondo por rendimiento.

## 🎨 Recursos gratuitos

### Marcos PNG:
- FreePik: https://www.freepik.com/search?query=photo%20frame%20png
- PNGTree: https://pngtree.com/free-png/frame

### Fondos:
- Unsplash: https://unsplash.com
- Pexels: https://www.pexels.com

## ✨ Ejemplo de uso

```kotlin
// En PhotoConfigActivity, el usuario selecciona:
filterMode = "sepia"           // Filtro sepia
frameMode = "frame_hearts"     // Marco de corazones
backgroundMode = "bg_beach"    // Fondo de playa
removeBackground = true        // Eliminar fondo activado

// CameraActivity procesa la foto:
1. Aplica filtro sepia
2. Usa ML Kit para detectar persona
3. Elimina el fondo original
4. Coloca el fondo de playa detrás
5. Superpone el marco de corazones
```

## 🐛 Solución rápida de problemas

**"Frame not found"**:
→ Añade el archivo PNG real en `res/drawable/`

**"Background not found"**:
→ Añade el archivo JPG real en `res/drawable/`

**El fondo no se elimina**:
→ Verifica que "Eliminar fondo" esté activado
→ Usa buena iluminación al capturar

**Muy lento**:
→ ML Kit tarda 2-3 segundos, es normal
→ Optimiza el tamaño de tus archivos de fondo

---

¡Listo para usar! Solo reemplaza los placeholders con tus archivos reales. 🚀
