# Mejor Equipo

App Android que lee el archivo **main** (formato exportación de Pokémon Showdown) de una carpeta que tú eliges
y construye los mejores equipos de 6 con tus Pokémon, ordenados de mejor a peor.

- **APK listo para instalar:** `dist/MejorEquipo.apk` (Android 7.0+; activa "instalar apps desconocidas").
- Al abrir la app **se vuelve a leer el main** de la carpeta y se recalculan los equipos.
- También escribe `equipos_generados.txt` en esa misma carpeta con todos los equipos (pegables en Showdown).

## Qué valora
Tipos (debilidades/resistencias, con habilidades como Levitate, Flash Fire, Intimidate…), cobertura ofensiva real de
tus movimientos, stats calculados con tus EVs/IVs/naturaleza/nivel, roles (hazards, pivotes, control de velocidad,
Fake Out, redirección, Trick Room/Tailwind, setup, prioridad…), sinergias de clima/terreno, equilibrio
físico/especial, objetos repetidos, tipos apilados y naturalezas que no encajan con los movimientos.
Modo **Singles** o **Dobles (VGC)**, número de equipos y opción de variar los equipos (mínimo 2 Pokémon distintos).

## Dos secciones
- **⚔️ Combate fuerte:** para jefes, líderes y Alto Mando. Puede usar bayas y objetos de un solo uso (Baya Zidra,
  bayas de resistencia, Banda Focus, Seguro Debilidad, Globo Helio…).
- **🌿 Aventura:** para el recorrido. Solo objetos que no se gastan; da más peso a la resistencia y la recuperación.
Cada Pokémon se muestra con su sprite, tipos, objeto con icono y movimientos. Sprites e iconos de PokeAPI/sprites.

## Objetos (Randomlocke)
Para cada Pokémon del equipo recomienda un objeto, explica por qué y de dónde sale. Las cantidades son reales:
- Objetos disponibles = los de tu **mochila** (`mochila.txt` en la misma carpeta, ej. `Restos x2`, `2x Vidasfera`,
  `Pañuelo Elegido`; vale español o inglés) **+** los que llevan equipados los Pokémon de tu `main`.
- Nunca se asigna el mismo objeto más veces de las que tienes. Si hay que quitárselo a otro Pokémon te dice a quién
  (priorizando los que no van en el equipo).
- Ejemplo en `sample/mochila.txt`. El `equipos_generados.txt` ya incluye los objetos recomendados.

## Guardado de Rubí Omega / Zafiro Alfa (3DS)
Si el `main` de la carpeta es el guardado binario de ORAS (483328 bytes, p. ej. copiado con Checkpoint), la app lo
lee directamente: equipo, cajas (nivel, naturaleza, EVs, IVs, habilidad, movimientos, objeto, Hidden Power) y mochila.
Un Pokémon que lleva su Megapiedra se evalúa como Mega (y se penaliza llevar más de una Mega por equipo).
Prueba interna en `engine-test/SaveTest.java` (guardado sintético cifrado con el algoritmo inverso).

## Formato del main (texto)
Un archivo `main.txt` (o `main`) con sets de Showdown separados por línea en blanco. Ejemplo en `sample/main.txt`.

## Desarrollo
```
./gradlew assembleRelease           # APK en app/build/outputs/apk/release/
node tools/build-data.js <ruta a pokemon-showdown/dist/data>   # regenera assets/*.tsv
javac -d /tmp/eo app/src/main/java/com/mejorequipo/engine/*.java engine-test/RunTest.java
java -cp /tmp/eo RunTest app/src/main/assets sample/main.txt [dobles]
```
