# InsuMap

InsuMap is an Android application developed using **Kotlin**, with **Firebase** and **Cloud Firestore** integration for backend data management.

The application simulates a dynamic insurance claims mapping workflow between a **Patient, Hospital, and Insurance Company** using **Policy and Bills PDF documents**.

---

## 📱 Overview

InsuMap demonstrates how insurance-related information can be digitally mapped between different stakeholders.

The application uses a Kotlin-based Android frontend with Firebase and Cloud Firestore for backend data management.

The project focuses on:

- Patient information
- Hospital information
- Insurance policy details
- Medical bills
- PDF-based policy and bill documentation
- Insurance claim mapping
- Firebase/Firestore data management

---

## 🛠️ Technologies Used

- **Kotlin** – Android application development
- **Android Studio** – Development environment
- **Firebase** – Backend services
- **Cloud Firestore** – Cloud database
- **Gradle** – Build and dependency management
- **PDF Documents** – Policy and medical bill information

---

## ✨ Features

- Native Android application built with Kotlin
- Dynamic mapping between:
  - Patient
  - Hospital
  - Insurance Company
- Insurance policy information management
- Medical bill documentation
- Policy and Bills PDF integration
- Firebase backend integration
- Cloud Firestore database
- Firestore security rules
- Android-based user interface

---

## 📂 Project Structure

```text
InsuMap/
│
├── app/
│   └── Android application source code
│
├── gradle/
│   └── Gradle wrapper files
│
├── build.gradle
├── settings.gradle
├── firebase.json
├── firestore.rules
├── gradle.properties
└── gradlew.bat
```
🚀 Getting Started
```text
Follow the steps below to clone and run InsuMap on your system.

1. Prerequisites

Before running the project, make sure you have:

Android Studio installed
Android SDK installed
JDK installed
Git installed
An Android device or Android Emulator

Recommended:

Latest stable Android Studio
JDK version compatible with the Gradle configuration used by the project
2. Clone the Repository

Open a terminal or Command Prompt and run:

git clone https://github.com/Rajath-MS-2006/InsuMap.git

Move into the project directory:

cd InsuMap
3. Open the Project in Android Studio
Open Android Studio.
Select Open.
Select the cloned InsuMap folder.
Wait for Android Studio to load the project.
Allow Gradle Sync to complete.

If Android Studio asks to install missing SDK components or Gradle dependencies, allow it to download them.

🔥 Firebase Setup

InsuMap uses Firebase and Cloud Firestore.

If you are cloning the project for development, you may need to configure your own Firebase project.

Step 1 – Create a Firebase Project

Go to:

https://console.firebase.google.com/

Create a new Firebase project.

Step 2 – Add an Android Application

Inside your Firebase project:

Select Add App.
Select Android.
Enter the Android package name used by the application.
Register the application.
Step 3 – Add Firebase Configuration

Download the Firebase configuration file:

google-services.json

Place it in the appropriate Android application directory:

InsuMap/
└── app/
    └── google-services.json

Important: Do not upload private credentials or sensitive Firebase configuration to a public repository.

Step 4 – Configure Cloud Firestore

Enable Cloud Firestore in the Firebase Console.

The repository contains:

firestore.rules

Review and configure the Firestore security rules according to your Firebase project requirements.

▶️ Running the Application

After completing the setup:

Connect an Android phone using USB debugging or start an Android Emulator.
Select the connected device in Android Studio.
Click the Run ▶ button.
Android Studio will build and install the application.

Alternatively, you can build the project using Gradle.

Windows
gradlew.bat assembleDebug

The generated APK can normally be found under:

app/build/outputs/apk/
📄 Policy and Bills PDFs

InsuMap uses Policy and Bills PDF documents as part of the simulated insurance claim workflow.

For testing, use appropriate sample or dummy documents.

Do not use real patient medical records, insurance documents, or personally identifiable information when testing or demonstrating the application.

🔐 Security

This project uses Firebase and Cloud Firestore.

When deploying your own version:

Configure your own Firebase project.
Review Firestore security rules.
Do not expose private credentials.
Do not commit sensitive API keys or credentials.
Use dummy data for demonstrations.
Avoid uploading real patient or insurance information.
🧪 Testing

To test the application:

Configure Firebase.
Run the application on an Android device or emulator.
Add or load the required sample policy and bill information.
Test the mapping between the Patient, Hospital, and Insurance Company.
Verify that the relevant Firestore data is being stored and retrieved correctly.
```
📌 Project Status

Development Project

InsuMap is a project implementation demonstrating an Android-based approach to digitally mapping insurance claim information between patients, hospitals, and insurance companies.

👨‍💻 Author

Rajath M S

B.Tech (Hons.) Computer Science and Engineering
RV University, Bengaluru

GitHub:
https://github.com/Rajath-MS-2006
