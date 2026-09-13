import 'dart:io';
import 'dart:async';
import 'dart:typed_data';
import 'dart:convert';
import 'package:path_provider/path_provider.dart';

class TurboNetworkService {
  ServerSocket? _serverSocket;
  RawDatagramSocket? _udpBroadcastSocket;
  RawDatagramSocket? _udpListenSocket;
  Timer? _broadcastTimer;
  
  final int tcpPort = 4040; // برای انتقال فایل
  final int udpPort = 4041; // برای پیدا کردن دستگاه‌ها

  // --- بخش گیرنده (Receiver) ---
  Future<void> startReceiving(String myDeviceName, Function(String) onStatusChanged) async {
    try {
      // ۱. باز کردن سرور دریافت فایل
      _serverSocket = await ServerSocket.bind(InternetAddress.anyIPv4, tcpPort);
      onStatusChanged("آماده دریافت. در حال جستجوی فرستنده...");

      _serverSocket!.listen((Socket client) async {
        onStatusChanged("فرستنده متصل شد! در حال دریافت...");
        Directory dir = await getApplicationDocumentsDirectory();
        // ذخیره فایل با نام یونیک
        File savedFile = File('${dir.path}/turbo_file_${DateTime.now().millisecondsSinceEpoch}.bin');
        IOSink fileSink = savedFile.openWrite();

        client.listen((Uint8List data) {
          fileSink.add(data);
        }, onDone: () async {
          await fileSink.close();
          client.close();
          onStatusChanged("فایل با موفقیت دریافت و ذخیره شد!");
        });
      });

      // ۲. ارسال سیگنال حضور (Broadcast) به کل شبکه
      _udpBroadcastSocket = await RawDatagramSocket.bind(InternetAddress.anyIPv4, 0);
      _udpBroadcastSocket!.broadcastEnabled = true;
      
      // هر دو ثانیه اسم دستگاه را در شبکه فریاد می‌زند!
      _broadcastTimer = Timer.periodic(const Duration(seconds: 2), (timer) {
        List<int> data = utf8.encode("TURBO:$myDeviceName");
        _udpBroadcastSocket!.send(data, InternetAddress("255.255.255.255"), udpPort);
      });

    } catch (e) {
      onStatusChanged("خطا در سیستم گیرنده: $e");
    }
  }

  void stopReceiving() {
    _serverSocket?.close();
    _broadcastTimer?.cancel();
    _udpBroadcastSocket?.close();
  }

  // --- بخش فرستنده (Sender) ---
  
  // جستجوی دستگاه‌های اطراف
  Future<void> startDiscovery(Function(String name, String ip) onDeviceFound) async {
    try {
      _udpListenSocket = await RawDatagramSocket.bind(InternetAddress.anyIPv4, udpPort);
      _udpListenSocket!.listen((RawSocketEvent event) {
        if (event == RawSocketEvent.read) {
          Datagram? dg = _udpListenSocket!.receive();
          if (dg != null) {
            String msg = utf8.decode(dg.data);
            if (msg.startsWith("TURBO:")) {
              String deviceName = msg.split(":")[1];
              String deviceIp = dg.address.address;
              onDeviceFound(deviceName, deviceIp);
            }
          }
        }
      });
    } catch (e) {
      print("خطا در جستجو: $e");
    }
  }

  void stopDiscovery() {
    _udpListenSocket?.close();
  }

  // ارسال فایل به دستگاه پیدا شده
  Future<void> sendFile(File file, String receiverIp, Function(String) onStatusChanged) async {
    try {
      onStatusChanged("در حال اتصال به $receiverIp...");
      Socket socket = await Socket.connect(receiverIp, tcpPort);
      onStatusChanged("متصل شد! در حال ارسال فایل...");
      
      await socket.addStream(file.openRead());
      await socket.flush();
      socket.close();
      onStatusChanged("ارسال با موفقیت انجام شد 🚀");
    } catch (e) {
      onStatusChanged("خطا در ارسال: $e");
    }
  }
}
