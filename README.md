# Duel

Mod Fabric pour Minecraft 1.21.1 : des duels entre joueurs dans une arène à part, sans rien risquer.

- `/duel` ouvre l'écran de duel : choisis ton adversaire (avec sa tête), l'arène (avec un
  aperçu) et le mode, puis **Défier**. La personne défiée reçoit un message avec **[Accepter]** et
  **[Refuser]**, cliquables.
- Deux sortes de duel : avec **ton équipement** (armure réparée à l'entrée), ou avec un **kit**
  identique pour les deux (Chevalier, Archer, Netherite) pour un combat loyal. En kit, tout ton
  équipement est mis de côté pendant le duel, y compris bijoux, élytre et sac à dos.
- Sans le mod côté client, ça marche aussi en commande : `/duel <joueur> [arène] [mode]`.
- Au lancement du duel, les autres joueurs reçoivent **[Regarder]** : un clic et tu arrives en
  spectateur au-dessus de l'arène.
- En entrant dans l'arène : PV et faim au maximum, effets retirés. Les duellistes passent en mode
  aventure (on se bat, mais sans poser ni casser de blocs).
- Compte à rebours de 3 secondes, puis combat. Le coup fatal ne tue pas : il fait perdre le duel.
  Pas de KO, pas de tombe, rien ne tombe par terre. Sortir de l'arène, c'est aussi perdre.
- À la fin, tout le monde retrouve exactement son état d'avant, puis revient à sa place :
  inventaire, usure de l'armure, potions et flèches utilisées, emplacements de bijoux et d'élytre,
  sac à dos, XP, PV, effets. Le duel ne coûte rien.
- Les spectateurs sont en mode spectateur : ils ne peuvent ni toucher ni être touchés, et les mobs
  les ignorent. `/duel quitter` pour partir avant la fin. Si tu es duelliste, `/duel quitter` veut
  dire abandonner.
- Un seul duel à la fois, 5 minutes maximum (égalité au-delà).

Une déconnexion ou un crash en plein duel ne fait rien perdre : l'état d'avant est gardé sur le
disque et rendu à la connexion suivante.

## Pour l'admin du serveur
- Mod requis côté serveur, recommandé côté client (écran `/duel`). Un joueur sans le mod peut se
  connecter et utiliser les commandes.
- Les arènes se trouvent dans une dimension vide, `duel:arena`, ajoutée par le mod, chacune à sa
  place. Pour chaque arène, une fois pour toutes :
  1. convertir le schéma Litematica en structure :
     `python3 tools/litematic_to_structure.py arene.litematic <monde>/generated/duel/structures/<nom>.nbt`
  2. la déclarer dans `config/duel.json` (`arenas` : structure, coin, points de départ, spectateur,
     centre, rayon) ;
  3. depuis la console : `duel admin arene <id>` (pose par tranches, quelques secondes).
- Aperçu dans l'écran : `config/duel/previews/<id>.png` (PNG, 16:9 conseillé, 900 Kio max), envoyé
  aux joueurs par le serveur.
- Kits : `kits` dans `config/duel.json`, avec les objets au format JSON du jeu (`id`, `count`,
  `components`) et les emplacements `head`, `chest`, `legs`, `feet`, `offhand` ou `0` à `35`. Un kit
  invalide est ignoré, avec une erreur dans le log.
- L'arène par défaut est « Blackstone Vault — The Molten Core » (Kowal_96, Planet Minecraft). Elle
  n'est pas fournie : le fichier appartient à son auteur.
- Durées : `config/duel.json`, écrit au premier démarrage. Un champ absent prend la valeur par
  défaut.
- `duel admin stop` : arrête le duel en cours (égalité).
- États d'avant duel en attente : `<monde>/duel/<uuid>.dat`. Un fichier ne reste là que si le
  retour du joueur a échoué, et le log dit pourquoi.

## Compiler
```
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew build runGametest
```
Le jar est dans `build/libs/`.

Licence GPL-3.0.
