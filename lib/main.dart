import 'dart:io';
import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import 'network_service.dart';

void main() {
  runApp(const TurboShareApp());
}

class TurboShareApp extends StatelessWidget {
  const TurboShareApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'TurboShare',
      theme: ThemeData(primarySwatch: Colors.blue),
      home: const TurboShareHome(),
    );
  }
}

class TurboShareHome extends StatefulWidget {
  const TurboShareHome({super.key});

  @override
  State<TurboShareHome> createState() => _TurboShareHomeState();
}

class _TurboShareHomeState extends State<TurboShareHome> {
  final TurboNetworkService _networkService = TurboNetworkService();
  String _status = "خوش آمدید به توربو شیر";
  final TextEditingController _ipController = TextEditingController();

  void _updateStatus(String status) {
    setState(() {
      _status = status;
    });
  }

  // عملیات دریافت
  void _receive() {
    _networkService.startReceiving(_updateStatus);
  }

  // عملیات ارسال
  Future<void> _send() async {
    FilePickerResult? result = await FilePicker.platform.pickFiles();
    if (result != null && result.files.single.path != null) {
      File file = File(result.files.single.path!);
      String ip = _ipController.text;
      if (ip.isNotEmpty) {
        _networkService.sendFile(file, ip, _updateStatus);
      } else {
        _updateStatus("لطفاً IP گیرنده را وارد کنید");
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('TurboShare 🚀')),
      body: Padding(
        padding: const EdgeInsets.all(20.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(_status, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold), textAlign: TextAlign.center,),
            const SizedBox(height: 40),
            TextField(
              controller: _ipController,
              decoration: const InputDecoration(
                labelText: 'IP دستگاه گیرنده (مثلا 192.168.1.5)',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 20),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                ElevatedButton.icon(
                  onPressed: _send,
                  icon: const Icon(Icons.send),
                  label: const Text('ارسال فایل'),
                  style: ElevatedButton.styleFrom(padding: const EdgeInsets.all(15)),
                ),
                ElevatedButton.icon(
                  onPressed: _receive,
                  icon: const Icon(Icons.download),
                  label: const Text('دریافت فایل'),
                  style: ElevatedButton.styleFrom(padding: const EdgeInsets.all(15), backgroundColor: Colors.green),
                ),
              ],
            )
          ],
        ),
      ),
    );
  }
}
