# platform/bluetooth

MVP'de Bluetooth/BLE cihaz entegrasyonu yoktur; bu yüzden manifestte Bluetooth izni istenmez.
Sonraki aşama: belgelenmiş GATT profilleri olan cihazlar için Companion Device Manager ile
sistem destekli eşleştirme, API 31+ için yalnızca BLUETOOTH_CONNECT/BLUETOOTH_SCAN
(neverForLocation) izinleri; konum izni istenmeyecek.
