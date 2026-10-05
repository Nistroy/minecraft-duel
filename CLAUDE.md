# minecraft-duel — instructions agents

Mod Fabric 1.21.1, **requis serveur, recommandé client** (écran `/duel` ; client sans le mod =
commandes seules : aucun registre statique ajouté, dimension de datapack synchronisée vanilla).
Duels 1v1 dans dimension vide `duel:arena`. Public, GPL-3.0. Pour `minecraft-server`.
Docs `.md` = notes denses pour agents, sauf `README.md` (humains).

## Carte du code
- `src/main/java/io/github/nistroy/duel/`
  - `rules/` (pur, testé) — `Challenges` défis + échéance · `Match` phases COUNTDOWN → FIGHT →
    FINISHED → `Teardown` après pause ; raisons KNOCKED_OUT, FORFEIT, LEFT_ARENA, TIME_UP (égalité),
    CANCELLED.
  - `config/DuelConfig` (testé) — `config/duel.json` partiel fusionné sur
    `resources/duel/default_config.json` ; `arenas` (`ArenaSpec` : structure, coin, départs,
    spectateur, centre/rayon/minY) + `kits` (`KitSpec`, objets JSON du jeu décodés côté serveur) ;
    `equipement` = mode sans kit, réservé. `KitSlot` head/chest/legs/feet/offhand/0..35.
  - `server/`
    - `SnapshotStore` (testé) — `<monde>/duel/<uuid>.dat`, écriture atomique, jamais écrasé.
    - `PlayerSnapshots` (gametest) — `saveWithoutId` complet + dimension/pos/mode ; restauration =
      `removeAllEffects` → `load` (Pos remplacée par position courante) → `teleportTo` → `setGameMode`
      → `resetSentInfo` + `sendAllPlayerInfo`.
    - `DuelService` — état (1 duel max, arène + kit du duel, spectateurs), commandes, événements.
    - `Kits` décodage (`ItemStack.CODEC` + registres ; invalide → ERROR, écarté) + `equip`
      (inventaire vidé + `compat/ModSlots`). `DuelMenu` envoi écran + aperçus (1×/session/joueur).
    - `Arena` dimension, sol posé ?, `clearEntities` · `ArenaBuilder` pose par tranches 32×32/tick ·
      `DuelCommand` `/duel` · `Texts` textes FR + boutons cliquables (`RUN_COMMAND`).
  - `compat/` — `ModSlots` vide Trinkets (`TrinketsApi`) et Accessories (`AccessoriesCapability`,
    cosmétiques gardés) si chargés ; classes chargées seulement si mod présent. Compilation seule
    (`modCompileOnly`, versions du serveur).
  - `network/` — `MenuPayload` S→C (adversaires + occupé, arènes, kits avec objets, duel en cours) ;
    `ArenaPreviewPayload` S→C (PNG ≤ 900 Kio). Aucun paquet C→S : l'écran envoie la commande
    `/duel <joueur> <arène> <mode>`.
  - `Duel` — branchements Fabric, lecture config (`SERVER_STARTING`), kits (`SERVER_STARTED`).
- `src/client/java/.../client/` — `DuelScreen` (3 colonnes : têtes `PlayerFaceRenderer`, arène en
  carrousel + aperçu recadré, modes + grille du kit avec infobulles) · `Previews` textures dynamiques
  `duel:preview/<arène>`, libérées à la déconnexion · `DuelClient` réception.
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
- Déconnexion en duel = abandon ; état rendu à la connexion suivante, **au tick d'après `JOIN`** : Fabric
  lance `JOIN` avant `PlayerList.placeNewPlayer` → `addNewPlayer` (bytecode 1.21.1) ; téléporter là = joueur
  dans 2 mondes → crash `DistanceManager.removePlayer` à la déconnexion suivante (live 2026-10-04).
  `DISCONNECT` peut tourner sur un thread Netty (`channelInactive`) → renvoyé au thread serveur.
  Arrêt serveur : rendus en `SERVER_STOPPING`.
- Arène par défaut : schéma Kowal_96 « Blackstone Vault — The Molten Core » (Planet Minecraft,
  `.litematic`, DataVersion 3955, 132×211×132). Non versionné (droits de l'auteur). Coin posé en
  `-73 0 -67` → centre de la croix `0 66 0` (sol y=65 : disque + bras 7 de large au-dessus d'une
  fosse ~y20, lave). Départs `-9.5/10.5 66 0.5` face à face, spectateur `7.5 82 28.5`.

## Décisions v0.2 (nistroy 2026-09-30)
- Vraie interface (écran client) plutôt que fenêtre d'inventaire : aperçu d'arène en image impossible
  en inventaire.
- Kits = combat loyal : même kit pour les deux, emplacements de mods vidés, tout rendu à la fin
  (restauration inchangée). Kits proposés : Chevalier, Archer, Netherite (config, sans release).
- Aperçus = PNG côté serveur (`config/duel/previews/<id>.png`) : arènes ajoutables sans release,
  schémas tiers hors dépôt. Rendu actuel : `~/minecraft-tools/duel/render_preview.py` (iso en coupe,
  hors dépôt ; capture en jeu possible à la place).
- Son « It's you and me » (Valorant, fichier fourni par nistroy 2026-10-04, assumé malgré droits
  Riot, dépôt public) : `assets/duel/sounds/its_you_and_me.ogg` (mp3 → ffmpeg encodeur `vorbis`
  natif, `libvorbis` absent), joué aux 2 duellistes à l'entrée (`DuelSounds`, catégorie `VOICE`).
  Jamais enregistré dans le registre : envoyé par id (`playNotifySound` → `Holder.direct`).
- Pas de vol plané dans `duel:arena` (nistroy 2026-10-05) : `Arena.allowsElytraFlight` sur
  `EntityElytraEvents.ALLOW`, enregistré en commun → bloque aussi côté client (sinon saccades). Elytra Slot
  `9.0.1` et Deeper Darker passent par cette API (vérifié dans les jars du serveur).
- Arènes tierces = config serveur seule (pas dans `default_config.json`). À venir : paris (après test réel).
- Migration v0.1 → v0.2 : ancien `config/duel.json` à plat → remplacé (champs inconnus ignorés, donc
  défauts) ; `duel admin arene` prend maintenant l'id.

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
plein duel. Écran `/duel` (pas d'affichage sur le Mac mini : `runClient` et captures impossibles),
vidage réel des emplacements Trinkets/Accessories en kit (mods absents du dev).
