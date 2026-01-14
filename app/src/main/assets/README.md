# 📁 Estructura de Carpetas - Marcos y Fondos

## 📂 assets/marcos/

Aquí van todos los **marcos decorativos** en formato PNG con transparencia.

### 📁 Temáticas disponibles:

- **verano/** - Marcos de verano (☀️ sol, 🏖️ playa, 🌴 palmeras, 🍦 helado)
- **navidad/** - Marcos navideños (🎄 árbol, ⛄ muñeco nieve, 🎅 Santa, 🎁 regalos)
- **cumpleaños/** - Marcos de cumpleaños (🎂 tarta, 🎈 globos, 🎉 confeti, 🎁 regalos)
- **frio/** - Marcos de invierno/frío (❄️ copos nieve, ⛸️ patines, 🧊 hielo)
- **comunion/** - Marcos de comunión (✝️ cruz, 👼 ángel, 🕊️ paloma, ⛪ iglesia)
- **boda/** - Marcos de boda (💍 anillos, 👰 novia, 🤵 novio, 💐 flores, ❤️ corazones)
- **otros/** - Marcos genéricos (🌟 estrellas, ❤️ corazones, 🖼️ clásicos)

### 📝 Especificaciones:

- **Formato**: PNG con canal alpha (transparencia)
- **Dimensiones**: 1080x1920 px (vertical) o 1920x1080 px (horizontal)
- **Centro**: Debe ser transparente para ver la foto
- **Decoraciones**: Solo en los bordes (esquinas, arriba, abajo)
- **Nombre**: Descriptivo, ejemplo: `marco_verano_playa_01.png`

### 📦 Ejemplo de estructura:

```
marcos/
  verano/
    marco_verano_sol_01.png
    marco_verano_playa_02.png
    marco_verano_palmeras_03.png
  navidad/
    marco_navidad_arbol_01.png
    marco_navidad_santa_02.png
  cumpleaños/
    marco_cumple_globos_01.png
    marco_cumple_confeti_02.png
```

---

## 📂 assets/fondos/

Aquí van todos los **fondos virtuales** en formato JPG o PNG.

### 📁 Temáticas disponibles:

- **verano/** - Fondos de verano (playa, piscina, tropical)
- **navidad/** - Fondos navideños (nieve, chimenea, árbol)
- **cumpleaños/** - Fondos de fiesta (decoraciones, colores)
- **frio/** - Fondos de invierno (nieve, montañas, paisaje)
- **comunion/** - Fondos religiosos (iglesia, jardín elegante)
- **boda/** - Fondos románticos (jardín, salón, atardecer)
- **otros/** - Fondos genéricos (colores, patrones, abstracto)

### 📝 Especificaciones:

- **Formato**: JPG (preferido) o PNG
- **Dimensiones**: 1080x1920 px (vertical) mínimo
- **Calidad**: Alta resolución
- **Nombre**: Descriptivo, ejemplo: `fondo_verano_playa_01.jpg`

### 📦 Ejemplo de estructura:

```
fondos/
  verano/
    fondo_verano_playa_01.jpg
    fondo_verano_piscina_02.jpg
  navidad/
    fondo_navidad_nieve_01.jpg
  boda/
    fondo_boda_jardin_01.jpg
```

---

## 🎨 Recomendaciones de diseño

### Para Marcos:
1. El centro debe ser completamente transparente
2. Decoraciones sutiles pero visibles
3. Usar colores que contrasten bien
4. No cubrir demasiado la foto (max 20% del área)

### Para Fondos:
1. Evitar fondos muy detallados o con texto
2. Usar colores que contrasten con personas
3. Fondos con profundidad de campo funcionan mejor
4. Evitar fondos muy oscuros o muy claros

---

## 📥 Cómo añadir contenido

1. **Coloca tus archivos** en la carpeta temática correspondiente
2. **Nomenclatura clara**: `tipo_tematica_descripcion_numero.ext`
3. **Recompila** la app
4. **La app detectará automáticamente** los nuevos archivos

No necesitas modificar código - el sistema es dinámico y escanea las carpetas.

---

## 🔍 Sistema de detección

La app escanea automáticamente:
- `assets/marcos/[tematica]/` → Busca archivos .png
- `assets/fondos/[tematica]/` → Busca archivos .jpg y .png

Las temáticas se generan dinámicamente según las carpetas que existan.

---

¡Sube tus archivos y disfruta! 🎉
