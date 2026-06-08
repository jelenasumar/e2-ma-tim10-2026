/**
 * Popunjava Firestore kolekcije sa primerima za igre.
 *
 * Priprema:
 *   1. Firebase konzola -> Project settings -> Service accounts -> Generate new private key
 *   2. Sacuvaj JSON kao scripts/serviceAccountKey.json (ne commituj u git!)
 *   3. cd scripts && npm install && npm run seed
 *
 * Ili postavi env promenljivu:
 *   set GOOGLE_APPLICATION_CREDENTIALS=C:\putanja\do\serviceAccountKey.json
 */

import { readFileSync, existsSync } from "fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";
import { initializeApp, cert, getApps } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

const __dirname = dirname(fileURLToPath(import.meta.url));
const keyPath = join(__dirname, "serviceAccountKey.json");

if (!process.env.GOOGLE_APPLICATION_CREDENTIALS && !existsSync(keyPath)) {
  console.error(
    "Nedostaje serviceAccountKey.json u scripts/ ili GOOGLE_APPLICATION_CREDENTIALS."
  );
  process.exit(1);
}

if (getApps().length === 0) {
  const credential = existsSync(keyPath)
    ? cert(JSON.parse(readFileSync(keyPath, "utf8")))
    : undefined;
  initializeApp({ credential });
}

const db = getFirestore();

const kzzQuestions = [
  {
    id: "q1",
    data: {
      active: true,
      text: "Koji je glavni grad Srbije?",
      options: ["Novi Sad", "Beograd", "Niš", "Kragujevac"],
      correctIndex: 1,
    },
  },
  {
    id: "q2",
    data: {
      active: true,
      text: "Koja je najduža reka koja protiče kroz Srbiju?",
      options: ["Tisa", "Sava", "Dunav", "Morava"],
      correctIndex: 2,
    },
  },
  {
    id: "q3",
    data: {
      active: true,
      text: 'Ko je autor romana "Na Drini ćuprija"?',
      options: ["Miloš Crnjanski", "Ivo Andrić", "Danilo Kiš", "Borislav Pekić"],
      correctIndex: 1,
    },
  },
  {
    id: "q4",
    data: {
      active: true,
      text: "Koliko planeta ima Sunčev sistem?",
      options: ["7", "8", "9", "10"],
      correctIndex: 1,
    },
  },
  {
    id: "q5",
    data: {
      active: true,
      text: "Koji hemijski simbol označava element zlato?",
      options: ["Ag", "Fe", "Au", "Cu"],
      correctIndex: 2,
    },
  },
  {
    id: "q6",
    data: {
      active: true,
      text: "Koji je najviši vrh Srbije?",
      options: ["Midžor", "Kopaonik", "Rtanj", "Tara"],
      correctIndex: 0,
    },
  },
];

const spojnicePuzzles = [
  {
    id: "sp1",
    data: {
      active: true,
      criterion: "Poveži izvođače sa nazivima njihovih pesama",
      leftTerms: ["Bajaga", "Đorđe Balašević", "Riblja Čorba", "Ekatarina Velika", "Zdravko Čolić"],
      rightTerms: ["Marlena", "Ne volim januar", "Kad hodaš", "Par godina za nas", "Moja tiho more"],
    },
  },
  {
    id: "sp2",
    data: {
      active: true,
      criterion: "Poveži glavne gradove sa državama",
      leftTerms: ["Beograd", "Pariz", "Berlin", "Rim", "Madrid"],
      rightTerms: ["Srbija", "Francuska", "Nemačka", "Italija", "Španija"],
    },
  },
  {
    id: "sp3",
    data: {
      active: true,
      criterion: "Poveži naučnike sa otkrićima",
      leftTerms: ["Newton", "Einstein", "Tesla", "Curie", "Galileo"],
      rightTerms: ["Gravitacija", "Relativnost", "Alternativna struja", "Radioaktivnost", "Teleskop"],
    },
  },
];

const associationPuzzles = [
  {
    id: "as1",
    data: {
      active: true,
      finalAnswer: "Pozoriste",
      columns: {
        A: { clues: ["Glumac", "Kostim", "Replika", "Uloga"], answer: "Predstava" },
        B: { clues: ["Zavesa", "Reflektor", "Scena", "Kulisa"], answer: "Bina" },
        C: { clues: ["Parter", "Loza", "Red", "Sediste"], answer: "Sala" },
        D: { clues: ["Aplauz", "Publika", "Pauza", "Premijera"], answer: "Izvodjenje" },
      },
    },
  },
  {
    id: "as2",
    data: {
      active: true,
      finalAnswer: "Biblioteka",
      columns: {
        A: { clues: ["Korice", "Strana", "Poglavlje", "Autor"], answer: "Knjiga" },
        B: { clues: ["Polica", "Katalog", "Clanska karta", "Pozajmica"], answer: "Citaonica" },
        C: { clues: ["Tisina", "Sto", "Lampa", "Beleznik"], answer: "Ucenje" },
        D: { clues: ["Roman", "Pesma", "Drama", "Prica"], answer: "Knjizevnost" },
      },
    },
  },
  {
    id: "as3",
    data: {
      active: true,
      finalAnswer: "Kafic",
      columns: {
        A: { clues: ["Espresso", "Mleko", "Secer", "Solja"], answer: "Kafa" },
        B: { clues: ["Kroasan", "Marmelada", "Pekara", "Jutro"], answer: "Dorucak" },
        C: { clues: ["Konobar", "Racun", "Sto", "Meni"], answer: "Usluga" },
        D: { clues: ["Prica", "Prijatelj", "Smeh", "Vreme"], answer: "Druzenje" },
      },
    },
  },
];

const korakPoKorakPuzzles = [
  {
    id: "kpk1",
    data: {
      active: true,
      answer: "Beograd",
      steps: [
        "Glavni grad jedne balkanske države",
        "Leži na ušću dve reke",
        "Prepoznatljiv po Kalemegdanu",
        "Sedište Narodne skupštine Srbije",
        "Ima mostove preko Save i Dunava",
        "Bivša prestonica Jugoslavije",
        "Naziv počinje slovom B",
      ],
    },
  },
  {
    id: "kpk2",
    data: {
      active: true,
      answer: "Sunce",
      steps: [
        "Nalazi se u centru Sunčevog sistema",
        "Izvor svetlosti i toplote za Zemlju",
        "Zvezda spektralnog tipa G",
        "Oko njega planete vrše revoluciju",
        "Sastoji se pretežno od vodonika",
        "Njegova energija nastaje fuzijom",
        "Vidljiv nam je danju sa neba",
      ],
    },
  },
];

async function seedCollection(collectionName, items) {
  const batch = db.batch();
  for (const item of items) {
    const ref = db.collection(collectionName).doc(item.id);
    batch.set(ref, item.data, { merge: true });
  }
  await batch.commit();
  console.log(`✓ ${collectionName}: ${items.length} dokumenata`);
}

async function main() {
  await seedCollection("kzz_questions", kzzQuestions);
  await seedCollection("spojnice_puzzles", spojnicePuzzles);
  await seedCollection("association_puzzles", associationPuzzles);
  await seedCollection("korak_po_korak_puzzles", korakPoKorakPuzzles);
  console.log("Seed završen.");
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
