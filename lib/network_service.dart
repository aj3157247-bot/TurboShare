import 'import 'dart:io';
import 'dart:typed_data';
import 'package:path_provider/path_provider.dart';

class TurboNetworkService {
  ServerSocket? _serverSocket;
  final int port = 4040; // پورت اختصاصی توربو شیر

  // ۱. کدهای گیرنده (Receiver)
  Future<void> startReceiving(Function(String) onStatusChanged) async {
    try {
      // گوش دادن به تمام آی‌پی‌های روی شبکه محلی
      _serverSocket = await ServerSocket.bind(InternetAddress.anyIPv4, port);
      onStatusChanged("آماده دریافت فایل. منتظر اتصال...");

      _serverSocket!.listen((Socket client) async {
        onStatusChanged("دستگاه فرستنده متصل شد!");
        
        // مسیر ذخیره فایل در گوشی
        Directory dir = await getApplicationDocumentsDirectory();
        File savedFile = File('${dir.path}/turbo_received_file.bin');
        IOSink fileSink = savedFile.openWrite();

        // دریافت بایت به بایت فایل و ذخیره با سرعت بالا
        client.listen((Uint8List data) {
          fileSink.add(data);
        }, onDone: () async {
          await fileSink.close();
          client.close();
          onStatusChanged("فایل با موفقیت دریافت و ذخیره شد!");
        });
      });
    } catch (e) {
      onStatusChanged("خطا در ایجاد سرور: $e");
    }
  }

  void stopReceiving() {
    _serverSocket?.close();
  }

  // ۲. کدهای فرستنده (Sender)
  Future<void> sendFile(File file, String receiverIp, Function(String) onStatusChanged) async {
    try {
      onStatusChanged("در حال اتصال به $receiverIp...");
      // اتصال به دستگاه گیرنده
      Socket socket = await Socket.connect(receiverIp, port);
      onStatusChanged("متصل شد! در حال ارسال فایل...");

      // خواندن فایل و ارسال آن به صورت استریم (برای جلوگیری از پر شدن رم)
      await socket.addStream(file.openRead());
      
      await socket.flush();
      socket.close();
      onStatusChanged("ارسال فایل با موفقیت انجام شد!");
    } catch (e) {
      onStatusChanged("خطا در ارسال: $e");
    }
  }
}
