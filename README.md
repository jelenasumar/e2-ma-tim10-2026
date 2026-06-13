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

Pored igara, aplikacija podržava profil korisnika, avatare, notifikacije, pozive za igru i online mečeve preko Firebase-a.

## Tehnologije

- Android aplikacija u Javi
- Gradle
- Firebase Authentication
- Cloud Firestore
- Firebase Admin SDK za seed skripte

## Pokretanje aplikacije

Projekat se pokreće iz Android Studija:

1. Otvori root folder projekta preko `File > Open...`.
2. Sačekaj da se završi Gradle Sync.
3. U gornjoj traci izaberi konfiguraciju `app`.
4. Izaberi fizički uređaj ili emulator.
5. Klikni `Run`.

Za fizički Android uređaj potrebno je uključiti `Developer options` i `USB debugging`, povezati uređaj USB kablom i potvrditi dozvolu `Allow USB debugging`.

Za emulator je potrebno otvoriti `Tools > Device Manager`, napraviti ili izabrati virtuelni uređaj i pokrenuti ga.

## Firebase podešavanje

Aplikacija koristi Firebase projekat `slagalica-5e6d9`. Za pun rad aplikacije potrebno je podesiti Authentication, Firestore pravila i Android konfiguracioni fajl.

### Android konfiguracija

Fajl `app/google-services.json` mora da postoji lokalno da bi aplikacija mogla da se poveže na Firebase.

Ako fajl nedostaje:

1. Otvori [Firebase konzolu](https://console.firebase.google.com/) i projekat `slagalica-5e6d9`.
2. Idi na `Project settings`.
3. U delu `Your apps` izaberi Android aplikaciju ili je dodaj ako ne postoji.
4. Preuzmi `google-services.json`.
5. Sačuvaj ga kao `app/google-services.json`.

Ovaj fajl je namerno ignorisan u gitu.

### Authentication

U Firebase konzoli otvori `Authentication -> Sign-in method` i uključi:

- `Email/Password`
- `Anonymous`

Bez anonimne prijave gost ne može da uđe u online tokove igre.

### Firestore

Firestore baza mora da bude uključena, a pravila moraju da odgovaraju fajlu `firestore.rules` iz root-a projekta.

Ručno podešavanje:

1. U Firebase konzoli otvori `Firestore Database -> Rules`.
2. Kopiraj sadržaj fajla `firestore.rules`.
3. Nalepi pravila u konzoli.
4. Klikni `Publish`.

Alternativa iz terminala, posle `firebase login`:

```bash
npx firebase-tools deploy --only firestore:rules --project slagalica-5e6d9
```

Na Windows-u može da se pokrene i:

```bat
setup-firebase.bat
```

Bez ovih pravila aplikacija pri pristupu lobiju ili meču može da prikaže grešku `PERMISSION_DENIED`.

## Seed podataka za igre

Seed skripte u folderu `scripts` pune Firestore primerima za igre:

- `kzz_questions`
- `spojnice_puzzles`
- `association_puzzles`
- `korak_po_korak_puzzles`

Detaljno uputstvo je u [scripts/README.md](scripts/README.md).

Ukratko:

```bash
cd scripts
npm install
npm run seed
```

Za seed je potreban service account ključ sačuvan lokalno kao `scripts/serviceAccountKey.json`. Taj fajl se ne dodaje u git.

Aplikacija prvo pokušava da učita podatke iz Firestore-a. Ako baza nije dostupna ili je prazna, koristi lokalni fallback sa hardkodovanim primerima.

## Korisni problemi i rešenja

`PERMISSION_DENIED` u lobiju ili meču:

- Objavi `firestore.rules`.
- Proveri da li je uključena anonimna prijava.
- Restartuj aplikaciju nakon izmene Firebase podešavanja.

Seed skripta ne radi:

- Proveri da li postoji `scripts/serviceAccountKey.json`.
- Pokreni `npm install` iz foldera `scripts`.
- Proveri da li service account pripada projektu `slagalica-5e6d9`.
