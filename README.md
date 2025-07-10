# ID-Document AutoCapture with TFLite and CameraX

An Android application that automatically detects and captures ID or document cards in real-time using a TensorFlow Lite (TFLite) object detection model and CameraX. The app ensures stable, non-blurry captures with smart auto-capture logic, leveraging AI to streamline document scanning without manual input.

## Features
- 📸 Real-time ID/document detection using TFLite
- 🤖 Auto-capture when the card is stable and clear
- 🌀 Blur detection to avoid capturing unclear images
- 🎯 Bounding box overlay with stability progress
- ⚡ Optimized for speed with CameraX and GPU support

## Tech Stack
- Kotlin
- CameraX
- TensorFlow Lite (YOLOv8 / Custom model)
- Android Jetpack Libraries

## Getting Started
1. Clone the repository  
2. Add your `.tflite` model and `labels.txt` inside `assets/`  
3. Update the paths in `Constants.kt`  
4. Build and run the app on a real device

## Developer Info
**Kundan Patil**  
📧 ptlkundan@gmail.com  
📱 +91 8668344573

---
