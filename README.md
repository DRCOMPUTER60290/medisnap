# MediSnap — Gestionnaire de médicaments Android

> Gestionnaire moderne et intuitif de médicaments, rappels intelligents, suivi d'observance et synchronisation cloud chiffrée de bout en bout.

[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84.svg?logo=android&logoColor=white)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Firebase](https://img.shields.io/badge/Firebase%20AI-Gemini-FFCA28.svg?logo=firebase&logoColor=black)](https://firebase.google.com)
[![RGPD](https://img.shields.io/badge/RGPD-Conforme-blue.svg)](https://drcomputer60290.fr/politique-de-confidentialite-medisnap/)
[![License](https://img.shields.io/badge/Licence-MIT-yellow.svg)](LICENSE)

## 📱 Aperçu

**MediSnap** est une application Android développée par **DrComputer60290** (Benjamin, Laigneville, Oise, France). Elle combine l'intelligence artificielle Google Gemini avec une architecture locale-first pour vous aider à gérer vos médicaments en toute sécurité et confidentialité.

🌐 [Page officielle](https://drcomputer60290.fr/medisnap-ia-medicale/) · 📋 [Politique de confidentialité](https://drcomputer60290.fr/politique-de-confidentialite-medisnap/)

---

## ✨ Fonctionnalités

### 🔍 Scan OCR de médicaments
- Photographiez la boîte de votre médicament
- **Gemini AI** (Firebase AI) extrait automatiquement : nom, dosage, forme galénique, instructions
- Identification rapide et fiable grâce à l'OCR intelligent

### ⏰ Rappels intelligents
- Programmez des rappels de prise personnalisés
- Notifications adaptées à votre horaire de traitement
- **Alertes de stock bas** automatiques

### 📊 Suivi d'observance thérapeutique
- Calculez votre taux d'observance en temps réel
- Historique complet de vos prises
- Visualisez vos progrès et restez sur la bonne voie

### 🔐 Synchronisation WebDAV chiffrée *(optionnel)*
- Sauvegardez sur votre **propre serveur WebDAV**
- Chiffrement **AES-256-GCM** de bout en bout
- Dérivation de clé **PBKDF2/HMAC-SHA256** avec 600 000 itérations (norme ANSSI/RGS)
- Votre serveur ne reçoit que des données illisibles sans votre mot de passe

### 🛡️ Sécurité & Confidentialité
- Données stockées **localement** sur votre appareil (Room Database)
- **Authentification biométrique** via Android BiometricManager
- Clés cryptographiques gérées par **Android Keystore**
- Aucune donnée transmise à DrComputer60290
- **100 % RGPD conforme** — données de santé sensibles protégées

---

## 🏗️ Architecture technique

### Stack

| Composant | Technologie |
|---|---|
| Langage | Kotlin |
| UI | Jetpack Compose + Material Design 3 |
| Base de données locale | Room (SQLite) + KSP |
| IA / OCR | Firebase AI (Gemini API) |
| Réseau | Retrofit + OkHttp + Moshi |
| Images | Coil Compose |
| Navigation | Navigation Compose |
| Authentification biométrique | Android BiometricManager |
| Sécurité | Android Keystore + AES-256-GCM |
| Sérialisation | Moshi + Kotlin codegen (KSP) |
| Concurrence | Kotlin Coroutines + Flow |
| Tests | JUnit + Robolectric + Roborazzi |
| Build | Gradle (Kotlin DSL) + Version Catalog |
| App Check | Firebase App Check (reCAPTCHA) |

### Prérequis
- **Android 7.0+** (minSdk 24)
- **Target Android 15** (SDK 36)
- Java 11+

---

## 🚀 Installation & Compilation

### Prérequis logiciels
- Android Studio Meerkat ou supérieur
- JDK 11+
- Compte Firebase avec un projet configuré

### 1. Cloner le dépôt

```bash
git clone https://github.com/DRCOMPUTER60290/medisnap.git
cd medisnap
```

### 2. Configurer Firebase

1. Créez un projet sur [console.firebase.google.com](https://console.firebase.google.com)
2. Activez **Firebase AI** (Gemini API)
3. Activez **Firebase App Check** (reCAPTCHA)
4. Téléchargez `google-services.json` et placez-le dans `app/`

### 3. Variables d'environnement

Copiez `.env.example` en `.env` à la racine :

```bash
cp .env.example .env
```

Renseignez les variables requises (clés API Firebase, etc.).

### 4. Compiler

```bash
# Debug
./gradlew assembleDebug

# Release (nécessite KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD)
./gradlew assembleRelease
```

---

## 🔒 Sécurité des données

MediSnap implémente un chiffrement de niveau professionnel :

| Mécanisme | Standard |
|---|---|
| Chiffrement des données synchronisées | AES-256-GCM |
| Dérivation de clé | PBKDF2/HMAC-SHA256, 600 000 itérations |
| Transport WebDAV | HTTPS exclusivement |
| Vérification d'intégrité | SHA-256 à chaque synchronisation |
| Stockage des clés | Android Keystore System |

---

## 📋 Conformité RGPD

Conformément au RGPD (Règlement UE 2016/679), l'utilisateur dispose de tous ses droits directement depuis l'application :

- **Accès** : consulter ses données dans l'application
- **Rectification** : modifier ses données depuis l'application
- **Effacement** : supprimer toutes ses données depuis l'application
- **Portabilité** : exporter via la synchronisation WebDAV

📄 [Consulter la politique de confidentialité complète](https://drcomputer60290.fr/politique-de-confidentialite-medisnap/)

---

## ⚠️ Avertissement médical

MediSnap est un **outil de gestion de médicaments** et ne remplace en aucun cas l'avis d'un professionnel de santé qualifié. Consultez toujours votre médecin ou pharmacien pour tout conseil médical, modification de traitement ou question sur vos médicaments.

---

## 👨‍💻 Auteur

**Benjamin** — DrComputer60290  
📧 webmaster@drcomputer60290.fr  
🌐 [drcomputer60290.fr](https://drcomputer60290.fr)  
📍 Laigneville, Oise (60), France

---

## 📄 Licence

Ce projet est sous licence MIT. Voir le fichier [LICENSE](LICENSE) pour plus de détails.
