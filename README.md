#  Where's My Bus? - Brașov Urban Navigator

**"Where's My Bus?"** este o aplicație mobilă modernă dezvoltată pentru platforma Android, menită să faciliteze utilizarea transportului public în municipiul Brașov. Proiectul integrează servicii de localizare geografică, baze de date în timp real și o interfață declarativă pentru a oferi o experiență de navigare urbană fluidă și eficientă.

---

## Stack Tehnologic & Arhitectură

Aplicația este construită respectând paradigmele actuale de **Modern Android Development (MAD)**, punând accent pe scalabilitate, reactivitate și o separare clară a responsabilităților (Separation of Concerns).

### Core & UI Framework
*   **Limbaj**: [Kotlin](https://kotlinlang.org/) - utilizând funcții avansate precum *Coroutines* pentru asincronism și *Flow* pentru procesarea stream-urilor de date reactive.
*   **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) - framework modern, declarativ, pentru construirea interfețelor native.
*   **Design System**: [Material Design 3](https://m3.material.io/) (Material You) - include suport nativ pentru **Dark Mode** (gestionat prin DataStore) și componente dinamice.
*   **Navigation**: Jetpack Compose Navigation pentru gestionarea stivei de ecrane și a argumentelor între rute.

### Data Persistence & Management
*   **Local Database**: [Room Persistence Library](https://developer.android.com/training/data-storage/room) - utilizată ca strat de cache local (Single Source of Truth) pentru stațiile favorite, asigurând funcționarea parțială offline.
*   **Key-Value Storage**: [Jetpack DataStore](https://developer.android.com/topic/libraries/architecture/datastore) (Preferences) - folosit pentru stocarea setărilor de tip lightweight, precum preferința pentru tema vizuală sau starea primului launch.
*   **Remote Backend**: [Firebase Firestore](https://firebase.google.com/docs/firestore) - bază de date NoSQL pentru stocarea stațiilor de autobuz și sincronizarea în timp real a datelor utilizatorului între dispozitive.

### Networking & API
*   **Authentication**: [Firebase Auth](https://firebase.google.com/docs/auth) - implementarea fluxurilor de Sign-In, Register și Password Update.
*   **Network Client**: [Retrofit 2](https://square.github.io/retrofit/) - utilizat pentru consumul API-ului de orare (real-time departures).
*   **Serialization**: [Moshi](https://github.com/square/moshi) - pentru parsarea performantă a răspunsurilor JSON.

### Maps & GIS (Geographic Information System)
*   **Maps SDK**: [Google Maps Platform](https://developers.google.com/maps) cu integrare prin [Maps Compose](https://github.com/googlemaps/android-maps-compose).
*   **Clustering**: Utilizarea `maps-compose-utils` pentru optimizarea randării markerilor (stațiilor) la nivele de zoom scăzute.
*   **Routing**: Randarea poliliniilor (Polyline) pentru reprezentarea traseelor liniilor de autobuz pe baza coordonatelor extrase din date tip OpenStreetMap (OSM).

---

##  Funcționalități Cheie

1.  **Harta Interactivă**: Vizualizarea stațiilor și a liniilor de transport (autobuz/troleibuz) cu diferențiere cromatică a rutelor.
2.  **Căutare Smart**: Filtrare avansată după numele stației sau numărul liniei, incluzând un algoritm de normalizare a textului pentru a ignora diacriticele.
3.  **Real-Time Schedule**: Afișarea timpului estimat de sosire (ETA) pentru următoarele curse, cu actualizare periodică automată la un interval de 30 de secunde.
4.  **Sincronizare Cloud-Local**: Stațiile favorite sunt salvate local în Room pentru acces instant și sincronizate în Firebase Firestore pentru persistență cross-device.
5.  **Seeding de Date**: Sistem automat de populare a bazei de date (Firebase) prin parsarea fișierelor JSON la prima execuție a aplicației.
6.  **Personalizare**: Suport complet pentru temă deschisă/închisă și gestionarea securizată a profilului de utilizator.

---

##  Structura Proiectului

Proiectul este organizat modular pentru a facilita mentenanța:
*   `data/`: Include entitățile Room, DAO-urile, implementarea DataStore și Provider-ul bazei de date.
*   `network/`: Conține clienții Retrofit, serviciile API și modelele de date pentru transfer (DTOs).
*   `screens/`: Logica de prezentare și componentele UI specifice fiecărui ecran (Main, Profile, Login, etc.).
*   `models/`: Definiții de date partajate și rutele de navigare.

---

##  Configurare Proiect

Pentru a rula proiectul local, sunt necesari următorii pași:

1.  **Firebase**: Adăugați fișierul `google-services.json` în folderul `app/`.
2.  **Maps API Key**: Definiți cheia în `local.properties`:
    