# TurboShare 2.0

## Added
- Turbo / Balanced / Battery Saver transfer modes.
- Larger adaptive transfer buffers for Turbo mode.
- Resume from `.part` files after interrupted transfers.
- SHA-256 integrity verification.
- Smart duplicate naming instead of overwriting received files.
- Multi-device receiver loop (multiple senders can connect sequentially/concurrently).
- LAN Web Transfer: a PC can upload a file to the phone from a browser.
- Transfer history with measured throughput.
- Google Play Billing 9.1 integration for a `turboshare_premium` one-time product.
- New TurboShare lightning icon and adaptive launcher icon.
- Version 2.0.0.

## Monetization plan
1. Free tier: core offline transfer remains free.
2. TurboShare Pro one-time purchase: remove future ads, unlock advanced transfer controls, priority features and premium themes.
3. Optional subscription later: cloud backup / cross-device migration / business features only. Do not put core offline transfer behind a subscription.
4. Business revenue: offer a white-label/business edition to repair shops, phone stores and small companies.

## Play Console setup
Create an in-app product with ID `turboshare_premium` and a one-time purchase offer. The app already contains the client-side billing integration. Configure pricing and testing in Play Console before production release.
