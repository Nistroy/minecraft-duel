# minecraft-duel — instructions agents

Mod Fabric 1.21.1, **serveur seul** (aucun contenu client ; dimension de datapack synchronisée
vanilla). Duels 1v1 dans dimension vide `duel:arena`. Public, GPL-3.0. Pour `minecraft-server`.
Docs `.md` = notes denses pour agents, sauf `README.md` (humains).

## Carte du code
- `src/main/java/io/github/nistroy/duel/`
  - `rules/` (pur, testé) — `Challenges` défis + échéance · `Match` phases COUNTDOWN → FIGHT →
    FINISHED → `Teardown` après pause ; raisons KNOCKED_OUT, FORFEIT, LEFT_ARENA, TIME_UP (égalité),
    CANCELLED.
  - `config/DuelConfig` (testé) — `config/duel.json` partiel fusionné sur `DEFAULT` ; validation.
  - `server/`
    - `SnapshotStore` (testé) — `<monde>/duel/<uuid>.dat`, écriture atomique, jamais écrasé.
    - `PlayerSnapshots` (gametest) — `saveWithoutId` complet + dimension/pos/mode ; restauration =
      `removeAllEffects` → `load` (Pos remplacée par position courante) → `teleportTo` → `setGameMode`
      → `resetSentInfo` + `sendAllPlayerInfo`.
    - `DuelService` — état (1 duel max, spectateurs), commandes, événements.
    - `Arena` dimension, sol posé ?, `clearEntities` · `ArenaBuilder` pose par tranches 32×32/tick ·
      `DuelCommand` `/duel` · `Texts` textes FR + boutons cliquables (`RUN_COMMAND`).
  - `Duel` — branchements Fabric, lecture config.
- `src/main/resources/data/duel/dimension{,_type}/arena.json` — vide (`flat`, `the_void`,
  `features:false`), y 0..256, nuit fixe, lit/ancre inopérants, pas de spawn (biome sans mobs).
- `src/gametest/` — mod de test `duel-gametest` (faux joueur Fabric).
- `tools/litematic_to_structure.py` (+ tests unittest) — `.litematic` 1 région → structure `.nbt`
  sans air.

## Décisions (nistroy 2026-09-30)
- Pas de vraie mort : `ALLOW_DEATH` → PV 1 + défaite. Écouteurs en phase `duel:before_other_mods`
  avant `DEFAULT_PHASE` : Balm/Hardcore Revival écoute `ALLOW_DEATH` en phase par défaut (vérifié
  `javap` `FabricBalmCommonEvents`, `KnockoutHandler`, 2026-09-30) → pas de KO, pas de tombe
  Universal Graves. Totem jamais consommé (Fabric appelle `ALLOW_DEATH` avant le totem).
- Tout l'inventaire rendu (pas seulement l'armure) : ferme les duplications (objet mis dans un sac
  d'un emplacement de mod pendant le duel, puis inventaire rendu). Armure réparée à l'entrée.
- Resynchro client des données de mods = changement de dimension au retour : CCA (Trinkets,
  Traveler's Backpack) synchronise dans `PlayerList.sendAllPlayerInfo` (mixin `MixinPlayerManager`),
  Accessories (dans Aether) sur `AFTER_PLAYER_CHANGE_WORLD`. Restaurer avant la téléportation.
- Duellistes en aventure (pas de blocs) ; arène jamais reposée automatiquement (1,5 M blocs) :
  `/duel admin arene` pour réparer.
- Spectateurs : mode spectateur, dégâts annulés (le vide tue même en spectateur), sortis de la
  dimension (menu de téléportation spectateur) → rendus aussitôt.
- Déconnexion en duel = abandon ; état rendu à la connexion suivante (`JOIN`). Arrêt serveur :
  rendus en `SERVER_STOPPING`.
- Arène par défaut : schéma Kowal_96 « Blackstone Vault — The Molten Core » (Planet Minecraft,
  `.litematic`, DataVersion 3955, 132×211×132). Non versionné (droits de l'auteur). Coin posé en
  `-73 0 -67` → centre de la croix `0 66 0` (sol y=65 : disque + bras 7 de large au-dessus d'une
  fosse ~y20, lave). Départs `-9.5/10.5 66 0.5` face à face, spectateur `7.5 82 28.5`.

## Tests — TDD obligatoire
- Red-Green-Refactor ; bug → test de régression d'abord ; jamais affaiblir un test.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew build runGametest`
  + `python3 -m unittest discover -s tools`.
- Fini = tout vert + `./gradlew runServer` propre (`Done (`) ; console du dev branchée sur stdin
  (`duel admin arene` utilisable). Arène de dev : convertir le schéma dans
  `run/world/generated/duel/structures/molten_core.nbt`.
- Le serveur GameTest ne charge pas les dimensions de datapack : tout ce qui touche `duel:arena` se
  vérifie en `runServer` (ex. `execute in duel:arena if block …` après `forceload`).
- CI `.github/workflows/ci.yml` sur chaque PR ; PR rouge jamais mergée.

## Règles
- Langue : identifiants EN ; docs, commentaires, textes joueurs FR ; commits/branches Conventional
  Commits EN.
- Git : jamais commit sur `main` ; branche `<type>/<sujet>` ; PR + merge via `gh`.
- Anti-invention : API vérifiée au `javap` sur les jars Loom (`.gradle/loom-cache/`) et les jars
  des mods du serveur.
- Release : tag `vX.Y.Z` (= `version` de `gradle.properties`) → workflow release → jar attaché.
- Ajout au serveur : `CLAUDE.md` de `minecraft-server` (accord, `backup-pre`, 0 joueur).

## Non vérifiable sans joueur humain
Déroulement complet à 2 joueurs (défi cliquable, compte à rebours, coup fatal → défaite, retour),
bouton Regarder, spectateur, resynchro client Trinkets/Accessories après retour, déconnexion en
plein duel.
