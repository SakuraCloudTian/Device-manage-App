# Device-manage-App

This project is a **QR Code-Based Device Management System**, designed to simplify the process of **registering, lending, returning, and managing devices** in labs or enterprises.  

It consists of an **Android front-end application** and a **Python backend service** integrated with a **MySQL database**.  
Additionally, it leverages the **Gemini API** to provide AI-powered responses for device issue reports.  

---

## 🔑 Features

- **Device & User Registration**  
  Register devices and users, automatically generating unique QR codes for future scanning.  

- **Device Lending & Returning**  
  Borrow or return devices by scanning the corresponding user and device QR codes. Status updates are reflected in real-time in the database.  

- **Issue Reporting (Report)**  
  Users can submit reports of device malfunctions. The backend uses **Gemini API** to generate intelligent suggestions for solutions.  

- **Device Overview (Show)**  
  View all devices and their current status (available, borrowed, or under maintenance).  

---

## 🖼️ App Screenshots

### 📌 Main Interface
<img width="540" height="1110" alt="Screenshot_20250930_131824" src="https://github.com/user-attachments/assets/5b44baa8-d709-440d-973a-9db3b3384241" />


### 📌 Lending Device (QR Code Scan)
<img width="540" height="1110" alt="Screenshot_20250930_131938" src="https://github.com/user-attachments/assets/1d064cc7-e509-4b28-9aaf-8682dd0d2506" />


### 📌 Register New Device
<img width="540" height="1110" alt="Screenshot_20250930_132005" src="https://github.com/user-attachments/assets/babde41c-3dc0-440f-9db1-a0f45842e139" />

---

## ⚙️ Tech Stack

### 📱 Frontend (Android App)
- **Language**: Kotlin  
- **Functions**: QR code scanning, device/user registration, lending, returning, reporting  
- **UI**: Simple Material Design  

### 🖥️ Backend
- **Language**: Python  
- **Framework**: Flask (HTTP service)  
- **Database**: MySQL (store devices, users, lending history, and reports)  
- **AI Service**: Gemini API for generating intelligent suggestions based on reports  

---

## 🏗️ System Architecture

```text
[Android App] <—HTTP—> [Flask Backend] <—SQL—> [MySQL Database]
                                    |
                                    └—> [Gemini API AI Suggestion]
