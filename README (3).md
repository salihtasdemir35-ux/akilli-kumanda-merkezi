# Akıllı Kumanda Merkezi — MVP 0.1.0

Android için yerel (Kotlin + Jetpack Compose) çoklu kumanda uygulaması. IR vericisi olan telefonlarda
TV'lere gerçek kızılötesi sinyal gönderir. IR'siz telefonlarda da kurulur ve neyin desteklenmediğini açıkça söyler.

## Mimari

Tek Android modülü, mantıksal paket ayrımı (`com.akillikumanda.app`):

| Paket | Sorumluluk |
|---|---|
| `core/model` | RemoteProfile, RemoteCommand, ProtocolData, enum'lar (Android'den bağımsız) |
| `core/database` | Room: `SavedRemoteEntity`, DAO, veritabanı |
| `core/designsystem` | Tema, `RemoteKey`, `StatusBanner`, simgeler |
| `data/catalog` | JSON katalog ayrıştırıcı + doğrulayıcı, katalog deposu, aday sıralayıcı |
| `data/remote` | Kayıtlı kumanda deposu, yedekleme/geri yükleme |
| `data/settings` | DataStore ayarları |
| `platform/infrared` | `IrHardware` (ConsumerIrManager), `IrEncoder`, `InfraredTransmitter` |
| `platform/capability` | `DeviceCapabilityRepository` donanım raporu |
| `platform/bluetooth`, `platform/network` | Sonraki aşama notları (bu sürümde kod yok) |
| `feature/*` | home, discovery (Kumanda Bul), remotes, tv, ac, settings, connections |
| `navigation` | Navigation Compose, alt gezinme |

MVVM: her ekranın ViewModel'i depoları kullanır. Bağımlılıklar `AppContainer` ile elle enjekte edilir.

### IR gönderimi
`Katalog profili → IrEncoder (NEC / Samsung32 / Sony SIRC12 / Philips RC5 / ham) → InfraredTransmitter → ConsumerIrManager.transmit()`

* Gönderim `Dispatchers.IO` üzerinde, `Mutex` ile sıralı yapılır. Desen 2 saniye ve 1024 öğe sınırıyla kontrol edilir.
* Frekans, `getCarrierFrequencies()` aralıklarıyla karşılaştırılır.
* Sonuç tipleri: `Success`, `UnsupportedHardware`, `UnsupportedFrequency`, `InvalidProfile`, `PermissionDenied`, `TransmissionError`, `Cancelled`.
* `Success` yalnızca "sinyal donanıma iletildi" demektir. Arayüz bunu "Sinyal gönderildi; cihaz tepkisini doğrulayın" olarak gösterir.

### Kumanda Bul
Kategori → marka/model/ad → iletişim yöntemi (telefona göre filtrelenir) → aday profiller (tam model > marka+kategori >
frekans uyumu > daha önce onaylanmış > kısmi model) → kontrollü test → kullanıcı onayı → kayıt.

* "Çalıştı/Çalışmadı" ancak gerçek bir sinyal gönderildikten sonra etkinleşir.
* Aday başına gönderim sınırı Ayarlar'dan 1–5 arasında seçilir (varsayılan 3). Sınırsız döngü yoktur.
* "Durdur" düğmesi her zaman vardır.
* Test komutu varsayılan olarak "Sessiz"dir; "Güç" en sona konur ve uyarıyla gösterilir.
* Hiçbir aday çalışmazsa hiçbir şey kaydedilmez.

### Veri
* `saved_remotes` tablosunda profilin JSON kopyası (`profileSnapshot`) tutulur. Böylece katalog güncellense de kayıtlı kumanda bozulmaz.
* (profileId + normalize ad) benzersizdir. Yinelenen kayıt reddedilir ve mevcut kayda yönlendirilir.
* Yedekleme ve geri yükleme JSON dosyasıyla yapılır (Android dosya seçicisi). Geri yüklemede yinelenenler atlanır.

## Katalog
`app/src/main/assets/catalog/catalog_v1.json`: 4 TV profili (Samsung32, LG NEC, Sony SIRC12, Philips RC5), 49 komut.
Değerler kamuya açık IR kod tablolarından alındı. Hepsi **`UNVERIFIED`**: gerçek bir TV'de test edilmedi.
Klima profili **yoktur**. Klimalar markaya özgü tam durum çerçeveleri kullanır ve doğrulanmamış kod eklenmedi.
Klima ekranı ve durum modeli hazırdır; kodlayıcı (`AcStateCodec`) eklendiğinde çalışır.

## Derleme

Gereken: JDK 17, Android SDK (API 34). En kolayı Android Studio (Koala veya daha yeni).

```bash
./gradlew testDebugUnitTest     # JVM birim testleri
./gradlew assembleDebug         # debug APK
# Windows:
gradlew.bat assembleDebug
```
APK çıktısı: `app/build/outputs/apk/debug/app-debug.apk`

Kurulum (USB hata ayıklama açık telefon): `adb install -r app/build/outputs/apk/debug/app-debug.apk`

### GitHub ile APK
Projeyi bir GitHub deposuna yükleyin. `.github/workflows/android.yml` her push'ta testleri çalıştırır ve debug APK üretir.
APK, **Actions → son çalıştırma → Artifacts → AkilliKumandaMerkezi-debug-apk** altından indirilir.

### Release APK imzalama
1. Anahtar oluşturun (bir kez yapılır, dosyayı ve şifreleri güvenli saklayın):
   `keytool -genkeypair -v -keystore akilli-kumanda.jks -keyalg RSA -keysize 2048 -validity 10000 -alias akillikumanda`
2. Proje köküne `keystore.properties` dosyası koyun (`.gitignore`'da, depoya girmez):
   ```
   storeFile=akilli-kumanda.jks
   storePassword=...
   keyAlias=akillikumanda
   keyPassword=...
   ```
3. `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`

## Sürümler
AGP 8.5.2 · Gradle 8.9 · Kotlin 2.0.20 · KSP 2.0.20-1.0.25 · Compose BOM 2024.09.03 · Room 2.6.1 ·
Navigation 2.8.2 · Lifecycle 2.8.6 · DataStore 1.1.1 · kotlinx-serialization 1.7.3 · compileSdk/targetSdk 34 · minSdk 26.

Bu küme, birbiriyle uyumlu olduğu bilinen bir settir; en yeni sürümler değildir. Yükseltme ayrı bir adımda yapılmalı.
Kotlin, KSP, serialization ve AGP birlikte yükseltilmeli. targetSdk 35+ için kenardan kenara ekran ve yerel ağ izinleri yeniden gözden geçirilmeli.

## Testler

| Test | Tür | Durum |
|---|---|---|
| IR kodlayıcı (NEC, Samsung, SIRC, RC5, hatalı değerler) | JVM | ✔ geliştirme ortamında Kotlin 2.0.20 ile çalıştırıldı |
| InfraredTransmitter: IR yok, hizmet yok, frekans dışı, frekans bilinmiyor, geçersiz profil, gönderim hatası, izin hatası, desen sınırı | JVM, **sahte donanım** | ✔ çalıştırıldı (gerçek donanım testi değildir) |
| Aday sıralama (tam/kısmi model, marka, onaylı profil, frekans) | JVM | ✔ çalıştırıldı |
| Klima durum modeli | JVM | ✔ çalıştırıldı |
| Katalog ayrıştırıcı (gerçek katalog, bozuk JSON, yanlış şema, boş katalog, geçersiz/yinelenen profil, snapshot) | JVM | Yazıldı; Gradle ile çalıştırılacak |
| Bağlantı politikası (IR yok, klima, araç, güvenli test komutları) | JVM | Yazıldı; Gradle ile çalıştırılacak |
| Room: kaydet/sil, yinelenen kayıt, yeniden açılışta kalıcılık, yedek geri yükleme | Cihaz testi (`connectedDebugAndroidTest`) | Yazıldı; emülatör veya cihaz gerekir |

### Bu ortamda test edilemeyenler
Gerçek IR gönderimi, frekans aralıklarının gerçek cihazdaki değerleri, Compose ekranlarının küçük ekranda
ve büyük yazı boyutunda görünümü, dosya seçiciyle yedekleme ve tam APK derlemesi. Bunlar aşağıdaki planla telefonda doğrulanmalı.

## Gerçek cihaz test planı
1. **IR'siz telefon:** Uygulama açılır. Ana ekranda IR uyarısı görünür. Kumanda Bul'da IR seçeneği devre dışıdır. Hiçbir şey kaydedilemez.
2. **IR'li telefon (Xiaomi/Redmi/POCO gibi):** Bağlantılar ekranında frekans aralıklarını not edin.
3. Gerçek bir TV ile Kumanda Bul → Sessiz testi → TV tepki verirse "Çalıştı" → kaydet. Sonra kumanda ekranındaki her tuşu deneyin.
   Çalışan ve çalışmayan tuşları not edin (bu notlarla profiller `LAB_VERIFIED` yapılabilir).
4. Ses tuşunu basılı tutun: tekrar etmeli, bırakınca durmalı.
5. Test sırasında "Durdur"a basın: sonrasında sinyal gitmemeli.
6. Uygulamayı kapatıp açın: kayıtlar durmalı. Yedekle → sil → geri yükle.
7. Sistem yazı boyutunu en büyüğe alın ve 5" ekranlı telefonda tüm ekranları kaydırarak kontrol edin.
8. TalkBack ile kumanda tuşlarını okutun.

## Bilinen kısıtlamalar
* Klima, ses sistemi, uydu, projeksiyon ve medya oynatıcı için katalogda profil yok.
* Wi-Fi, Bluetooth/BLE, üretici entegrasyonları ve harici IR aksesuarı yok. Arayüz bunları "kullanılamıyor" olarak gösterir.
* Araç sistemleri yalnızca bilgilendirme içerir. RF anahtar kopyalama ve benzeri işlevler kasıtlı olarak yoktur.
* Etiket fotoğrafı ile model okuma yok.
* Ağdan katalog indirme yok (dolayısıyla imza veya bütünlük doğrulaması da henüz gerekmedi).
* `ConsumerIrManager.transmit()` engelleyici bir çağrıdır. Başlamış tek bir gönderim yarıda kesilemez; "Durdur" sonraki gönderimleri engeller.
