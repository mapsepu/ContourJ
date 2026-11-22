# ContourJ MIM 2026 — cambios sobre ContourJ 0.1.0-dev

Fork de trabajo de [Gonxolo/ContourJ](https://github.com/Gonxolo/ContourJ) (commit `af97ba8`), hecho en el
curso Procesamiento de Imágenes y Bioseñales II, Magíster en Informática Médica, Universidad de Chile (2026).
Estado: **beta, en prueba**.

## Versión 0.2.0-beta

Corrige errores de la interfaz, de la entrada y de la salida. **No cambia el método numérico:** con los
mismos parámetros, las coordenadas del contorno ajustado son idénticas a las de 0.1.0-dev (comprobado
ejecutando las clases de ambas versiones sobre el mismo caso de prueba, 9 combinaciones de parámetros).

| # | Problema en 0.1.0-dev | Cambio |
|---|---|---|
| E1 | Con Windows en español (coma decimal) los seis parámetros decimales no se podían editar | El campo usa siempre punto decimal |
| E2 | La ventana podía mostrar un valor y el ajuste usar otro: el enlace entre ambos se perdía al limpiar memoria (`asObject()` sin referencia) | Se guardan las referencias; el enlace ya no se corta |
| E3 | "Load parameters" fallaba con `NoClassDefFoundError: org/tomlj/Toml` | Lectura propia del archivo, sin la librería `tomlj`; el selector de métrica se actualiza al cargar |
| E4 | El resultado era una línea abierta (`FREELINE`): Fiji no medía el área y el largo omitía el tramo de cierre | El resultado es un polígono cerrado |
| E5 | No se informaba cuántas iteraciones se hicieron ni por qué se detuvo | Cada Run escribe en la ventana Log: imagen, parámetros usados y, por ROI, puntos, iteraciones, motivo de parada, última variación y perímetro inicial y final |
| E6 | Un error durante el ajuste no se mostraba | Se muestra un aviso y se registra en el Log |
| E7 | El resultado tenía el mismo nombre que la ROI inicial | Se llama `<nombre inicial>-ContourJ` y conserva el plano (Z/T) de la ROI inicial |
| E8 | El campo vectorial quedaba en memoria toda la vida de la ventana: cambiar imagen, μ o iteraciones del campo no tenía efecto hasta reabrir el plugin | El campo se recalcula en cada Run |
| E9 | Re-muestrear una ROI de entrada ya cerrada (primer punto repetido al final) lanzaba `ArrayIndexOutOfBoundsException` | El contador del punto de cierre se incrementa solo si se agrega |
| E10 | Seis opciones de salida y la entrada "Mask" se mostraban pero no hacían nada | Quedan desactivadas hasta implementarse |
| E17 | Cada ventana de ContourJ cerrada seguía recibiendo los eventos de imágenes (después de abrir el plugin tres veces, cada evento se procesaba tres veces) | La ventana deja de escuchar al cerrarse |
| E18 | Al cerrar una imagen podía saltar un `NullPointerException` no capturado y abrirse la consola de Fiji; las listas de imágenes se rehacían en cada evento y llenaban la consola de mensajes | El error se captura y la lista se refresca en el siguiente evento; las listas se rehacen solo si cambian; los mensajes de rutina pasan a nivel de depuración |

El título de la ventana muestra la versión del plugin. Nuevo botón **"Restore default parameters"**, bajo Save/Load parameters.

## Pendiente (cambia los resultados; no incluido)

Umbral de convergencia y valores por defecto; matriz armada con el punto de cierre repetido; re-muestreo
en cada iteración según el perímetro; interpolación del campo entre píxeles; plano correcto en stacks.

## Instalación

Copiar `ContourJ-0.2.0-beta.jar` en `Fiji.app/plugins/` y **quitar** `ContourJ-0.1.0-dev.jar` (las dos versiones
juntas entran en conflicto). Reiniciar Fiji. Menú: Plugins > Segmentation > ContourJ.
