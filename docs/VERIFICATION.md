# Vérifications — 27 octobre 2026

**Compilation réussie** : Reminder 1.0.0, code 241, variante coreDebug, applicationId `io.reminder.calendar`. APK : `reminder-core-debug.apk`, signature APK v2 vérifiée avec `apksigner`. Minimum API 26, cible API 34.

**25 tests exécutés, 25 réussis, aucun ignoré** : 9 tests du modèle temporel, 15 tests du moteur avec Robolectric Android API 28 / SQLite / notifications simulées, 1 migration complète Room 8→9 avec validation du schéma et conservation d’un événement. Rapport : `app/build/reports/tests/testCoreDebugUnitTest/index.html`.

Commande exécutée avec JDK 17 / Gradle 8.2.1 :

```sh
./gradlew clean testCoreDebugUnitTest assembleCoreDebug --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2048m -Pksp.incremental=false
```

La compilation initiale a nécessité de remplacer le composant rtl-viewpager JitPack indisponible par ses sources originales épinglées. Un cache KSP 1.9.10 devenu incohérent après le renommage des packages a été éliminé par `clean` et la désactivation de son mode incrémental pour cette vérification. Le build propre réussit. Des avertissements de dépréciation et de chaînes de traduction viennent encore de la base amont.

SHA-256 de l’APK livré : `65bfce668ec083cc33f0711a31d375f4e41cbf33b4fd3614fab0ae19a9783fde`.

Les contrôles Android réels ci-dessous ne sont pas assimilés aux simulations JVM.

Autres régressions vérifiées : refus de report à l’échéance ou au-delà, doublon de déclenchement après report, exceptions de récurrence, suppression d’événement, actualisation des métadonnées, retour d’un événement à son ancien horaire, modification du réglage global sans réécriture des échéances, changement de fuseau d’un événement existant. Les tests de récurrence utilisent le moteur d’occurrences existant.

## Matrice appareil à exécuter

Utiliser une installation de test, avec autorisations de notifications et d’alarmes précises accordées. Créer des événements d’essai avec une durée personnalisée de **0,05 minute (3 secondes)**, puis de **0,5 minute (30 secondes)** pour les manipulations de redémarrage. Conserver un second événement avec une autre durée.

| Scénario | Résultat attendu | Exécution dans cette session |
| --- | --- | --- |
| 9 h + 3 h | Retrait à 12 h | Réussi en test de calcul ; aucun test de 3 h sur appareil |
| Démarrage à 13 h | Aucune réapparition du rappel expiré | Réussi en simulation du moteur |
| Actualisation à 11 h | Même échéance à 12 h | Réussi en simulation du moteur |
| Balayage manuel puis redémarrage | Aucune réapparition | Réussi via le moteur et la réouverture SQLite |
| Rappels simultanés / plusieurs emplacements | Échéances et actions indépendantes | Réussi en simulation du moteur |
| Application retirée des applications récentes | Retrait à l’échéance | Non exécuté sur appareil |
| Écran verrouillé | Retrait à l’échéance ; respect de la confidentialité | Non exécuté sur appareil |
| Doze | Pas de prolongation si déclenchement retardé ; retrait par Android | Non exécuté sur appareil |
| Redémarrage Android réel | Restauration silencieuse des rappels valides uniquement | Non exécuté sur appareil |
| Refus des autorisations | Enregistrement conservé, aucune notification imposée | Réussi en simulation ; interface système non testée |

Pour Doze : débrancher l’appareil, fermer l’application, puis `adb shell dumpsys deviceidle force-idle`. Après observation, `adb shell dumpsys deviceidle unforce`. Vérifier séparément la boucle sonore/vibrante activée, un report avant échéance et un report atteignant l’échéance. Redémarrer avant puis après expiration. Ne pas confondre fermeture normale et « Forcer l’arrêt », qui suspend les récepteurs Android jusqu’au prochain lancement.

## Blocage des essais appareil

Aucun appareil physique n’a été fourni. Le SDK et une image Android 9 x86_64 ont été installés, mais l’émulateur 37.2.12 s’arrête par **SIGSEGV (code 139)** immédiatement après la détection de l’image, même avec accélération désactivée et rendu logiciel. `/dev/kvm` et les informations `/proc` ne sont pas accessibles. Le serveur adb ne démarre pas non plus dans cet environnement. Les scénarios écran verrouillé, veille, redémarrage réel et installation réelle restent donc à exécuter.
