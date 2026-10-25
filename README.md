# Bed Stalker (Minecraft 1.20.1 · Forge 47.x · GeckoLib 4.4.x)

Mob hostil alto (2.9 bloques) que acecha al jugador, se esconde bajo las camas, rompe puertas y ventanas y ataca por sorpresa.

## Compilar
Requisitos: JDK 17.
1. Falta `gradle-wrapper.jar` (binario). Cópialo (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) del MDK oficial de Forge 1.20.1, o ejecuta `gradle wrapper --gradle-version 8.1.1` si tienes Gradle. También puedes abrir la carpeta en IntelliJ IDEA e importarla como proyecto Gradle.
2. `./gradlew build` -> `build/libs/bedstalker-1.0.0.jar`
3. Prueba: `./gradlew runClient` / `./gradlew runServer`.

Para jugar con el .jar necesitas también GeckoLib `geckolib-forge-1.20.1-4.4.9` en `mods/`.
Si 4.4.9 no existe en el repositorio, cambia `geckolib_version` en `gradle.properties`.

## Comportamiento
- **Acecho**: se acerca despacio, intentando ponerse a tu espalda. Si lo miras, se congela.
- **Agresión**: tras ~0.6 s de mirada, o si te acercas a menos de 3.5 bloques, o si lo dañas, chilla y te persigue más rápido (~7 s, se renueva).
- **Cama**: estando de acecho a 6-40 bloques, puede ir a una cama cercana (≤16 bloques de él y ≤28 de ti), se agacha y se desliza debajo (hitbox reducida, casi oculto por el colchón). Sale y ataca si te acercas a <3.5 bloques, si lo hieren, si duermes cerca o si rompen la cama. Solo se esconde si hay hueco libre de 2.9 bloques para salir.
- **Destrucción**: puertas de madera (3 s), de hierro (8 s), cristal y paneles (1 s) con progreso de grietas. Si no hay ruta (ventana, puerta de hierro) camina en línea recta hasta el obstáculo y lo rompe. Respeta `mobGriefing`.
- **Spawn raro**: peso 2 en el Overworld, solo en oscuridad; de noche 1/4 de los intentos, o 1/2 si hay una cama a menos de 48 bloques. Ajusta `data/bedstalker/forge/biome_modifier/stalker_spawns.json`.
- **Stats**: 100 vida, 8 armadura, 4 dureza, 0.6 resistencia a empuje, 9 de daño, -40% daño de proyectiles.

## Sonidos
Registrados en `ModSounds` (registro común, cliente y servidor) y enlazados en `assets/bedstalker/sounds.json`. Los .ogg son sintéticos: reemplázalos por los tuyos con el mismo nombre (mono, ogg vorbis) o edita/regenera con `python3 tools/generate_assets.py`.
- Pasos pesados y lentos (una de cada dos zancadas al acechar; más fuertes a tu espalda).
- Respiración y susurros cuando está cerca (<16 bloques) y no lo miras, también bajo la cama.
- Rotura: sonido normal del bloque (`destroyBlock`) + `smash` grave. Golpes `bang` mientras trabaja.
- `hide` / `emerge` al esconderse y salir.

## Configuración
`config/bedstalker-common.toml`: activar/desactivar romper puertas, hierro, ventanas, tiempos y esconderse bajo camas.
Tags de datapack: `bedstalker:breakable_wooden_doors`, `breakable_iron_doors`, `breakable_windows` (puedes añadir bloques de otros mods).

## Ajustes de animación
Editables en `tools/generate_assets.py` (o directamente en los JSON). Si algún brazo/torso se inclina hacia el lado contrario al esperado, invierte el signo del eje X en esa animación. `ENTER_TICKS` / `EXIT_TICKS` en `StalkerEntity` deben coincidir con la duración de `enter_bed` (1.5 s) y `exit_bed` (1.0 s).

## Comandos de prueba
`/summon bedstalker:stalker` o el huevo en la pestaña de huevos de creativo.
