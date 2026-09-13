# FastSend

FastSend is an Android-to-Android direct file transfer app focused on a very simple UI and high-throughput local TCP transfer.

## Included
- Android 8+ (minSdk 26)
- Wi-Fi Direct device discovery and connection
- Multi-file selection
- Large file streaming with 1 MiB buffers
- TCP_NODELAY
- SHA-256 integrity check
- Interrupted-transfer continuation through `.part` files
- Transfer progress
- Transfer history
- Dark/light system theme
- GitHub Actions APK build
- No account or cloud server required

## Important Android behavior
Wi-Fi Direct behavior varies by phone manufacturer and Android version. On some devices the user must enable Wi-Fi and allow Nearby devices permission. The first release intentionally avoids a cloud relay so the file path stays local.

## Build with GitHub
1. Create a GitHub repository.
2. Upload the contents of this project to the repository root.
3. Open **Actions**.
4. Run **Build FastSend APK**.
5. Open the successful workflow run and download **FastSend-debug-apk** from Artifacts.

GitHub Actions creates the Gradle wrapper during the build, so a wrapper binary does not need to be committed.

## How to use
### Receiver
Open FastSend → **Receive files**. Keep the screen open.

### Sender
Open FastSend → **Select files** → **Find nearby devices** → tap the receiver phone.

## Current scope
This release is the complete Android transfer core. Windows desktop transfer and QR-based pairing can be added as a separate companion client without changing the transfer protocol.
