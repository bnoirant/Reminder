# Notification lifecycle

`ReminderEngine` serializes scheduling, delivery, dismissal, snooze and recovery. `ReminderStore` commits to SQLite synchronously before cancellation. Its key contains the full 64-bit event ID, occurrence start, reminder slot (0/1/2), planned UTC milliseconds and a schedule fingerprint. Notification identity is `(tag=key, id=1)`. PendingIntents use an explicit component, an action-specific URI containing the entire key, and `FLAG_IMMUTABLE`; extras and truncated integer IDs are not used for uniqueness.

States: scheduled → shown → dismissed/expired/cancelled, or shown → snoozed → shown. Expiry is exclusive: `now >= expires` suppresses the delivery. Snooze changes only `delivery`, never `planned` or `expires`. Duplicate deliveries respect the persisted snooze. Terminal tombstones prevent resurrection, including after clock rollback. Display metadata and server `lastUpdated` do not affect the schedule fingerprint; a content refresh cannot create a new deadline. Recurrence exceptions are validated against the existing occurrence generator.

`NotificationReceiver` holds `goAsync()` until work completes, using a single executor. The platform owns timeout and repeated sound/vibration (`FLAG_INSISTENT`). Cancellation of one tag stops only its alert. Opening a notification records dismissal directly in the target activity, including `onNewIntent`; swiping sends a deleteIntent. App recovery reconciles missing active posts. Boot count distinguishes boot recovery from ordinary process creation, so Application initialization cannot discard valid posts before the boot receiver runs.

Recovery includes still-valid missed occurrences even if the event has ended, and schedules the next future occurrence for each of the three reminder slots. Following delivery or expiry it advances the chain. Boot, package replacement, time/time-zone changes, exact-alarm grants and foreground resume reconcile the ledger. Rejected notification permission consumes the current delivery. Exact-alarm refusal uses `setAndAllowWhileIdle`; no permission is bypassed.

Room schema 8→9 adds nullable `notification_visibility_ms` with SQL default NULL. NULL inherits the preference, whose absent value is 14,400,000 ms. Existing events and all earlier migrations remain. The separate reminder ledger starts at schema 1. ICS preserves overrides with `X-REMINDER-VISIBILITY-MS`; imports without it and CalDAV refreshes preserve an existing local override. Configuration backups include the global duration.

## Official Android references consulted 2026-10-27

- https://developer.android.com/reference/android/app/Notification.Builder#setTimeoutAfter(long) — relative platform timeout (API 26), supplied with remaining time on every post/update.
- https://developer.android.com/reference/android/app/Notification.Builder#setDeleteIntent(android.app.PendingIntent) — explicit user dismissal callback; notification taps are handled separately by the activity.
- https://developer.android.com/reference/android/app/PendingIntent — Intent filter identity and immutable PendingIntents.
- https://developer.android.com/develop/background-work/services/alarms — exact-alarm access, revocation and reprogramming, idle constraints, reboot restoration.
- https://developer.android.com/develop/ui/views/notifications/notification-permission — Android 13+ POST_NOTIFICATIONS and refusal.

Platform limits remain: force-stop suppresses app callbacks until the user launches it again; manufacturer battery restrictions can delay delivery. No application can issue an alarm while the device is powered off. Android notification timeout removes already posted alerts independently of the app process. Lock-screen privacy and channel settings remain controlled by the user. Physical-device verification of these OS behaviours must be reported separately from JVM simulation.
