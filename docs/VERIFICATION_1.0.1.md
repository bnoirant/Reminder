# Reminder 1.0.1

- Commons source GPL: commit b7dd6ad428fdaa72362ce04cc1dbfa407f4c2fa8, corrections documentées dans simple-commons/REMINDER_PATCHES.md. Le préfixe de package déclenchait un avertissement trompeur au lancement et bloquait la personnalisation. Ces restrictions sont supprimées ; les demandes d'autorisations Android restent actives.
- Widgets mensuel et liste : taille cible 4×2, hauteur initiale minimale 110dp, hauteur de redimensionnement minimale 110dp. Le rendu mensuel compact (<280dp) garde 42 jours, indications d'événements, accessibilité et clics ; les titres reviennent en mode agrandi. Six lignes de poids égal.
- Référence officielle consultée : https://developer.android.com/develop/ui/views/appwidgets/layouts (targetCellHeight à partir d'Android 12, minHeight pour les versions précédentes, options de taille et orientations).
- Tests ajoutés : rendu mensuel à 250×110dp, six semaines, clics, indicateur et accessibilité ; retour des titres en mode agrandi ; métadonnées des deux widgets ; absence d'avertissement aux lancements 0/100 et personnalisation après 100 ; identité Reminder.
- Commande : `./gradlew testCoreDebugUnitTest assembleCoreDebug --no-daemon` (JDK17, SDK34).
- Résultats de compilation et tests : à compléter après exécution.
- Pas de téléphone/lanceur réel disponible : redimensionnement tactile et affichage sur écran d'accueil non exécutés. Aucun test physique de veille/verrouillage/redémarrage exécuté dans cette correction.
- Signature : clé privée non publiée. La clé de test 1.0.0 n'est plus disponible ; exporter les données avant toute réinstallation nécessitée par une signature différente.
