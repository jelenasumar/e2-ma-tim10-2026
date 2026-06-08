# Seed podataka za igre (Firestore)

Skripta unosi primer pitanja i zagonetki u Firebase kolekcije:

| Kolekcija | Igra |
|-----------|------|
| `kzz_questions` | Ko zna zna |
| `spojnice_puzzles` | Spojnice |
| `association_puzzles` | Asocijacije |
| `korak_po_korak_puzzles` | Korak po korak |

## Pokretanje

1. U [Firebase konzoli](https://console.firebase.google.com/) otvori projekat `slagalica-5e6d9`.
2. Project settings → Service accounts → **Generate new private key**.
3. Sačuvaj fajl kao `scripts/serviceAccountKey.json` (ne dodavaj u git).
4. Iz foldera `scripts`:

```bash
npm install
npm run seed
```

5. Objavi pravila čitanja (ako još nisu):

```bash
cd ..
firebase deploy --only firestore:rules
```

Aplikacija prvo učitava podatke iz Firestore-a. Ako baza nije dostupna ili je prazna, koristi se lokalni fallback (hardkodovani primeri u kodu).
