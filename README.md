# Duel

Mod Fabric pour Minecraft 1.21.1 : des duels entre joueurs dans une arène à part, sans rien risquer.

- `/duel <joueur>` pour défier quelqu'un. La personne défiée reçoit un message avec
  **[Accepter]** et **[Refuser]**, cliquables.
- Au lancement du duel, les autres joueurs reçoivent **[Regarder]** : un clic et tu arrives en
  spectateur au-dessus de l'arène.
- En entrant dans l'arène : PV, faim et durabilité de l'armure au maximum, effets retirés. Les
  duellistes passent en mode aventure (on se bat, mais sans poser ni casser de blocs).
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
- Mod côté serveur seulement : les joueurs n'ont rien à installer.
- L'arène se trouve dans une dimension vide, `duel:arena`, ajoutée par le mod. Une fois pour
  toutes :
  1. convertir le schéma Litematica en structure :
     `python3 tools/litematic_to_structure.py arene.litematic <monde>/generated/duel/structures/molten_core.nbt`
  2. depuis la console : `duel admin arene` (pose par tranches, quelques secondes).
- L'arène par défaut est « Blackstone Vault — The Molten Core » (Kowal_96, Planet Minecraft). Elle
  n'est pas fournie : le fichier appartient à son auteur.
- Points de départ, rayon, durées : `config/duel.json`, écrit au premier démarrage.
- `duel admin stop` : arrête le duel en cours (égalité).
- États d'avant duel en attente : `<monde>/duel/<uuid>.dat`. Un fichier ne reste là que si le
  retour du joueur a échoué, et le log dit pourquoi.

## Compiler
```
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew build runGametest
```
Le jar est dans `build/libs/`.

Licence GPL-3.0.
