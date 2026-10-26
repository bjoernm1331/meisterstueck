# Meisterstück: Android-App für das Fire-Tablet

Eigenständige App, die direkt mit deiner TaHoma im lokalen Netz spricht. Kein Server, kein Internet
bei der Präsentation. Du baust die App-Datei (APK) einmalig mit GitHub und installierst sie auf dem Tablet.

## Teil 1: Developer Mode an der TaHoma (einmalig, mit Internet)

1. In der **TaHoma-App** (by Somfy): Installation konfigurieren, Parameter öffnen.
2. Die **PIN der TaHoma siebenmal antippen**. Dadurch erscheint der Developer Mode.
3. Im Developer-Menü einen **Token erzeugen** und sicher speichern (er wird nur einmal angezeigt).
4. Falls der Developer Mode nicht erscheint: TaHoma-Firmware aktualisieren lassen und erneut versuchen.

## Teil 2: APK mit GitHub bauen (einmalig)

1. Diese ZIP-Datei auf dem Computer entpacken.
2. Auf github.com ein **neues Repository** anlegen (privat ist in Ordnung), zum Beispiel `meisterstueck`.
3. Im leeren Repository auf **„uploading an existing file“** klicken und den **gesamten Inhalt** des
   entpackten Ordners hineinziehen (nicht den Ordner selbst). Mit **Commit changes** bestätigen.
4. Prüfen, ob im Repository der Ordner `.github/workflows` mit der Datei `build-apk.yml` angekommen ist.
   Versteckte Ordner werden beim Hochladen manchmal übersprungen. Falls er fehlt:
   **Add file, Create new file**, als Namen `.github/workflows/build-apk.yml` eingeben und den Inhalt
   der mitgelieferten Datei `build-apk.yml.txt` einfügen.
5. Oben auf **Actions** gehen. Der Bau startet von selbst (sonst „APK bauen“, dann **Run workflow**).
   Er dauert etwa 3 bis 5 Minuten.
6. Wenn der Lauf grün ist: den Lauf öffnen und unten bei **Artifacts** `Meisterstueck-APK` herunterladen.
   Die heruntergeladene ZIP enthält `app-debug.apk`.
7. Wird der Lauf rot: den Fehlertext kopieren und mir schicken.

## Teil 3: Auf dem Fire-Tablet installieren

1. Die `app-debug.apk` auf das Tablet bringen (zum Beispiel per USB-Kabel, E-Mail oder Cloud-Speicher).
2. Auf dem Tablet unter **Einstellungen, Sicherheit und Datenschutz** das Installieren
   **aus unbekannten Quellen** für die App erlauben, mit der du die Datei öffnest (Dateimanager oder Silk).
3. Die APK antippen und installieren. Danach erscheint **Meisterstück** bei den Apps.

## Teil 4: Netzwerk für die Präsentation

- Du brauchst ein Netz, in dem **Tablet und TaHoma** hängen: ein kleiner Reiserouter ohne Internet reicht.
  Die TaHoma kommt per Netzwerkkabel an den Router (oder per WLAN, falls dein Modell das kann).
- Gib der TaHoma im Router eine **feste IP-Adresse**, damit sie sich nicht ändert.
- **Zu Hause vorher komplett testen**, mit ausgeschaltetem Internet.

## Teil 5: Erste Verbindung in der App

1. Meisterstück öffnen, **IP-Adresse der TaHoma** und **Token** eintragen, Anmelden.
2. Beim ersten Verbinden merkt sich die App das Zertifikat der TaHoma. Ändert es sich später
   (zum Beispiel nach einem Firmware-Update), meldet die App das. Dann **Abmelden** und neu verbinden.

## Was funktioniert, was nicht

- **Funktioniert:** Geräte anzeigen und steuern, Smoove-Taster mit vier Kanälen, Lichtwert des Sunis io,
  Live-Anzeige von Änderungen.
- **Gibt es nicht:** TaHoma-Szenarien. Die lokale Schnittstelle der TaHoma bietet sie nicht an.
- **Unsicher:** Einige io-Geräte melden lokal „nicht verfügbar“, obwohl sie funktionieren. Die App ignoriert
  diese Meldung. Ob dein Sunis io seinen Lichtwert regelmäßig sendet, zeigt erst der Test an deiner Box.
- **Adresse:** Bitte die IP-Adresse eintragen, nicht den Namen `gateway-….local`. Android löst solche
  Namen nicht zuverlässig auf.

## Sicherheit

- Der Token liegt nur auf dem Tablet und wird nie an die Oberfläche der App weitergegeben.
- Die App prüft das Zertifikat der TaHoma nicht gegen die Somfy-Zertifizierungsstelle, sondern merkt sich
  den Fingerabdruck beim ersten Verbinden. Verbinde dich deshalb beim ersten Mal nur in deinem eigenen Netz.
- Die APK ist eine Debug-Version. Für den privaten Gebrauch reicht das.

## Voraussetzung am Tablet

Die Oberfläche läuft in der Android-WebView. Auf einem aktuellen Fire OS (Fire HD mit Fire OS 8) ist das
unproblematisch. Bei sehr alten Tablets bitte „Android System WebView“ im Play Store aktualisieren.
