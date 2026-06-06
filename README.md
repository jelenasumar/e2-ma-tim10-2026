# Slagalica

Android aplikacija po uzoru na kviz Slagalica.

## Opis aplikacije

Aplikacija sadrži igre:

- Ko zna zna
- Spojnice
- Asocijacije
- Skočko
- Korak po korak
- Moj broj

Kao i prikaz profila i notifikacija.

## Pokretanje aplikacije

Projekat se pokreće iz Android Studija. Potrebno je otvoriti root folder projekta preko opcije `File > Open...` i sačekati da se završi Gradle Sync. 

Nakon toga se u gornjoj traci bira konfiguracija `app` i uređaj na kom će se aplikacija pokrenuti.

Aplikacija može da se pokrene na dva načina:

- Na fizičkom Android uređaju: potrebno je uključiti `Developer options` i `USB debugging`, povezati uređaj USB kablom i potvrditi dozvolu `Allow USB debugging`.
- Na emulatoru: potrebno je otvoriti `Tools > Device Manager`, napraviti ili izabrati postojeći virtuelni uređaj i pokrenuti ga.

Kada se uređaj pojavi u Android Studiju, izabrati ga iz liste target uređaja i kliknuti `Run`.

## Firebase (obavezno za online igru)

U [Firebase konzoli](https://console.firebase.google.com/) za projekat `slagalica-5e6d9`:

1. **Authentication → Sign-in method** — uključi **Email/Password** i **Anonymous**.
2. **Firestore Database → Rules** — kopiraj sadržaj fajla `firestore.rules` iz root-a projekta i klikni **Publish**.

Bez ovih pravila aplikacija pri pristupu lobiju/meču prikazuje grešku `PERMISSION_DENIED`.

Alternativa iz terminala (posle `firebase login`):

```bash
npx firebase-tools deploy --only firestore:rules --project slagalica-5e6d9
```
