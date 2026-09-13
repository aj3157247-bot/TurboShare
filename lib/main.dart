import 'import 'dart:io';
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
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
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
  String _status = "آماده اتصال...";
  
  // لیست دستگاه‌های پیدا شده در اطراف
  Map<String, String> _discoveredDevices = {}; 
  bool _isDiscovering = false;

  void _updateStatus(String status) {
    setState(() {
      _status = status;
    });
  }

  // حالت گیرنده (Receiver)
  void _startReceiving() {
    setState(() => _discoveredDevices.clear());
    // اسم فرضی دستگاه شما (می‌توانید از پکیج device_info_plus استفاده کنید)
    String myDeviceName = "Galaxy S23 (Turbo)"; 
    _networkService.startReceiving(myDeviceName, _updateStatus);
  }

  // حالت فرستنده (Sender)
  void _startDiscovering() {
    setState(() {
      _isDiscovering = true;
      _discoveredDevices.clear();
      _status = "در حال جستجوی دستگاه‌های اطراف...";
    });
    
    _networkService.startDiscovery((name, ip) {
      setState(() {
        _discoveredDevices[ip] = name; // اضافه کردن دستگاه به لیست
      });
    });
  }

  // انتخاب و ارسال فایل به دستگاه انتخاب شده
  Future<void> _sendFileTo(String ip) async {
    FilePickerResult? result = await FilePicker.platform.pickFiles();
    if (result != null && result.files.single.path != null) {
      File file = File(result.files.single.path!);
      _networkService.sendFile(file, ip, _updateStatus);
    }
  }

  @override
  void dispose() {
    _networkService.stopReceiving();
    _networkService.stopDiscovery();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('TurboShare 🚀', style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          children: [
            Container(
              padding: const EdgeInsets.all(15),
              decoration: BoxDecoration(
                color: Colors.grey.shade200,
                borderRadius: BorderRadius.circular(10),
              ),
              child: Text(
                _status, 
                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                textAlign: TextAlign.center,
              ),
            ),
            const SizedBox(height: 30),
            
            // دکمه‌های اصلی
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                ElevatedButton.icon(
                  onPressed: _startDiscovering,
                  icon: const Icon(Icons.search),
                  label: const Text('ارسال فایل (جستجو)'),
                  style: ElevatedButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 15)),
                ),
                ElevatedButton.icon(
                  onPressed: _startReceiving,
                  icon: const Icon(Icons.download),
                  label: const Text('دریافت فایل'),
                  style: ElevatedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 15),
                    backgroundColor: Colors.green.shade100,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 30),
            
            // لیست دستگاه‌های پیدا شده
            if (_discoveredDevices.isNotEmpty) ...[
              const Align(
                alignment: Alignment.centerRight,
                child: Text("دستگاه‌های اطراف:", style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
              ),
              const SizedBox(height: 10),
              Expanded(
                child: ListView.builder(
                  itemCount: _discoveredDevices.length,
                  itemBuilder: (context, index) {
                    String ip = _discoveredDevices.keys.elementAt(index);
                    String name = _discoveredDevices.values.elementAt(index);
                    
                    return Card(
                      elevation: 3,
                      margin: const EdgeInsets.only(bottom: 10),
                      child: ListTile(
                        leading: const CircleAvatar(child: Icon(Icons.phone_android)),
                        title: Text(name, style: const TextStyle(fontWeight: FontWeight.bold)),
                        subtitle: Text(ip),
                        trailing: ElevatedButton(
                          onPressed: () => _sendFileTo(ip),
                          child: const Text("ارسال"),
                        ),
                      ),
                    );
                  },
                ),
              ),
            ] else if (_isDiscovering) ...[
              const CircularProgressIndicator(),
              const SizedBox(height: 10),
              const Text("در حال گشتن... (مطمئن شوید گیرنده روی 'دریافت' کلیک کرده است)"),
            ]
          ],
        ),
      ),
    );
  }
}
