# Seed podataka za igre (Firestore)

Skripta unosi primer pitanja i zagonetki u Firebase kolekcije:

| Kolekcija | Igra |
|-----------|------|
| `kzz_questions` | Ko zna zna |
| `spojnice_puzzles` | Spojnice |
| `association_puzzles` | Asocijacije |
| `korak_po_korak_puzzles` | Korak po korak |

## Priprema

1. U [Firebase konzoli](https://console.firebase.google.com/) otvori projekat `slagalica-5e6d9`.
2. Idi na `Project settings -> Service accounts`.
3. Klikni `Generate new private key`.
4. Sačuvaj fajl kao `scripts/serviceAccountKey.json`.

`serviceAccountKey.json` se ne dodaje u git.

Umesto lokalnog fajla može da se koristi i env promenljiva `GOOGLE_APPLICATION_CREDENTIALS` koja pokazuje na service account JSON.

Windows primer:

```bat
set GOOGLE_APPLICATION_CREDENTIALS=C:\putanja\do\serviceAccountKey.json
```

## Pokretanje

Iz foldera `scripts`:

```bash
npm install
npm run seed
```

## Firestore pravila

Ako pravila čitanja još nisu objavljena, iz root foldera projekta pokreni:

```bash
npx firebase-tools deploy --only firestore:rules --project slagalica-5e6d9
```

Aplikacija prvo učitava podatke iz Firestore-a. Ako baza nije dostupna ili je prazna, koristi lokalni fallback sa hardkodovanim primerima u kodu.
