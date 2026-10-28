# Reminder

Calendrier Android dérivé de **Simple Calendar**, avec des notifications à échéance fixe.
Nom : **Reminder** · applicationId/namespace : **`io.reminder.calendar`** · Android **8.0+** (API 26).
L’APK de test s’installe à côté de Simple Calendar. Les variantes core, fdroid et prepaid partagent volontairement la même identité Reminder.

Les données d’une installation Simple Calendar peuvent être transférées par export/import ICS ; les deux applications ont des espaces de données Android distincts. La migration du schéma interne Reminder conserve les événements existants.

Le calendrier existant est conservé : événements, tâches, récurrences et exceptions, rappels, widgets, synchronisation CalDAV, import/export ICS et sauvegardes.

## Notifications

Dans Paramètres → Durée de visibilité des notifications : **4 heures** initialement, choix **1/2/4/8 h**, ou durée personnalisée positive en minutes décimales (précision milliseconde). Chaque événement peut choisir sa durée ou hériter du réglage global.

L’échéance est toujours **heure prévue du rappel + durée**. Un rappel de 9 h valable 3 h expire à 12 h, même reçu à 11 h. Un rappel reçu à 13 h est ignoré. Une actualisation ou un report conserve l’échéance ; un report atteignant ou dépassant celle-ci est refusé. Retirer une notification ne supprime jamais l’événement.

L’état de chaque événement/occurrence/emplacement de rappel est enregistré dans SQLite. Android retire la notification avec `setTimeoutAfter(tempsRestant)` ; une alarme d’expiration complète ce mécanisme. Les suppressions manuelles sont persistées. Après redémarrage, seuls les rappels encore valides sont restaurés, silencieusement. Le son répétitif reste géré par Android et s’arrête avec la notification ; aucun service audio autonome n’est ajouté.

Hypothèses : la durée est figée quand le rappel est enregistré ; changer le réglage global concerne les nouveaux rappels enregistrés. Une modification de l’horaire ou de la règle d’un événement invalide son ancienne programmation. Les échéances déjà enregistrées sont des instants UTC ; les futures occurrences locales suivent le moteur de récurrence et les changements de fuseau. Un refus de notifications est respecté ; les événements restent enregistrables. Sans autorisation d’alarmes précises, le déclenchement peut être retardé, sans prolonger la visibilité. Le réglage « Rappels précis » ouvre l’autorisation système à la demande.

## Compilation et installation

Prérequis : JDK 17, SDK Android 34 et licences SDK acceptées. Le wrapper utilise Gradle 8.2.1 / AGP 8.1.1. Définir `ANDROID_HOME` ou `sdk.dir` dans `local.properties`.

```sh
./gradlew testCoreDebugUnitTest assembleCoreDebug
adb install -r app/build/outputs/apk/core/debug/reminder-core-debug.apk
```

L’APK debug est signé automatiquement avec une clé de développement, sans clé privée de publication. Pour une version de production, renseigner `keystore.properties` à partir de l’exemple et lancer `assembleCoreRelease`. Ne pas publier la clé de développement.

La CI [Reminder Android](https://github.com/bnoirant/Reminder/actions/workflows/android.yml) teste, compile et conserve l’APK avec le rapport de tests. Voir [le bilan 1.0.1 des vérifications](docs/VERIFICATION_1.0.1.md) ([bilan 1.0.0](docs/VERIFICATION.md)) et [la conception](docs/NOTIFICATIONS.md).

## Origine et licences

Le dépôt contenait déjà l’application ; aucune réinitialisation n’a été faite. Base conservée : **`599985c678bd786e024dbd421cfe1708656eea45`**, issue de [SimpleMobileTools/Simple-Calendar](https://github.com/SimpleMobileTools/Simple-Calendar). Historique, [GPL-3.0](LICENSE), attributions et [README original](docs/UPSTREAM_README.md) conservés. Reminder est un dérivé indépendant.

Simple-Commons reste épinglé à `b7dd6ad428`. Son ancienne dépendance JitPack `rtl-viewpager:940f12724f` n’est plus disponible ; ses deux fichiers Java inchangés sont inclus depuis le commit **`940f12724ff0fe624f12e8218b2b967891aa5396`** de [Duolingo/rtl-viewpager](https://github.com/duolingo/rtl-viewpager), avec leur [licence Apache-2.0](third_party/rtl-viewpager/LICENSE).

### Version 1.0.1 : widgets compacts

Les widgets mensuel et liste peuvent être ajoutés ou redimensionnés en **4×2**. Sous 280 dp de hauteur, le mois montre les six semaines avec un point pour les événements ; toucher un jour ouvre son agenda. Agrandir le widget rétablit les titres. Le lanceur décide des dimensions exactes des cellules. Après mise à jour, un widget existant peut nécessiter d'être retiré puis ajouté à nouveau.

Simple-Commons est désormais compilé depuis les sources GPL conservées dans `simple-commons/` ; voir `simple-commons/REMINDER_PATCHES.md`. Les restrictions du préfixe Simple Mobile Tools qui provoquaient le faux avertissement ont été retirées.

Les APK de test sont signés avec une clé de développement privée hors du dépôt. La clé éphémère de la version 1.0.0 n'est plus disponible : si Android refuse une mise à jour pour signature différente, **exportez événements, tâches et réglages avant toute désinstallation**, puis réinstallez et importez vos données. Pour une distribution durable, utilisez votre propre clé de signature conservée en sécurité.
