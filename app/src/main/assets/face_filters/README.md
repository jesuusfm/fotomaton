# Filtros Faciales - Face Filters

Esta carpeta contiene los filtros faciales que se pueden aplicar a los videos.

## Estructura de carpetas

Cada tipo de filtro tiene su propia carpeta:

```
face_filters/
├── mustache/     # Bigotes (se posicionan debajo de la nariz)
├── hat/          # Gorros/Sombreros (se posicionan encima de la cabeza)
├── glasses/      # Gafas (se posicionan sobre los ojos)
├── mask/         # Máscaras (cubren toda la cara)
├── ears/         # Orejas (orejas de conejo, gato, etc.)
├── nose/         # Narices (nariz de payaso, animal, etc.)
└── full/         # Overlay completo (cara completa con efectos)
```

## Cómo añadir filtros

1. **Formato**: PNG con fondo transparente
2. **Tamaño recomendado**: 512x512 píxeles (o proporcional)
3. **Nombre**: Sin espacios, usa guiones bajos (ej: `bigote_elegante.png`)

## Ejemplos de nombres

- `mustache/bigote_clasico.png`
- `mustache/bigote_cowboy.png`
- `hat/gorro_navidad.png`
- `hat/sombrero_mexicano.png`
- `glasses/gafas_sol.png`
- `glasses/gafas_fiesta.png`
- `mask/mascara_veneciana.png`
- `ears/orejas_conejo.png`
- `nose/nariz_payaso.png`
- `full/cara_perro.png`

## Posicionamiento automático

El sistema usa **ML Kit Face Detection** para detectar las caras y posicionar automáticamente los filtros:

- **mustache**: Se posiciona entre la nariz y la boca
- **hat**: Se posiciona encima de la cabeza
- **glasses**: Se posiciona sobre los ojos
- **mask**: Cubre toda la cara
- **ears**: Se posiciona encima de la cabeza (más ancho)
- **nose**: Se posiciona sobre la nariz
- **full**: Overlay grande que cubre toda la cara

## Tips para crear buenos filtros

1. Usa imágenes con **fondo transparente (PNG)**
2. El filtro debe estar **centrado** en la imagen
3. Para bigotes: el bigote debe estar centrado horizontalmente
4. Para gorros: la parte inferior del gorro debe tocar el borde inferior de la imagen
5. Para gafas: centradas horizontal y verticalmente
6. Para orejas: las orejas deben estar en la parte superior

## Recursos gratuitos para filtros

Puedes encontrar filtros PNG gratuitos en:
- [Flaticon](https://www.flaticon.com/)
- [PNGTree](https://pngtree.com/)
- [FreePik](https://www.freepik.com/)
- [Pixabay](https://pixabay.com/)

Busca términos como: "mustache png transparent", "party hat png", "funny glasses png", etc.
