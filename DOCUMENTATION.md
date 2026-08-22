# SM Ads SDK - Integration Documentation (ડોક્યુમેન્ટેશન)

આ ડોક્યુમેન્ટેશન SM Apps Studio માટે બનાવવામાં આવેલ **SM Ads SDK** ને કોઈપણ એન્ડ્રોઇડ એપમાં સરળતાથી ઈન્ટિગ્રેટ કરવા માટેનું માર્ગદર્શન પૂરું પાડે છે. આ SDK સંપૂર્ણપણે ગૂગલની નવીનતમ પોલિસીઓ અને સિક્યોરિટી ગાઈડલાઈન્સ મુજબ બનાવવામાં આવ્યું છે.

---

## ૧. કન્ફિગરેશન ફાઇલ ઉમેરવી (`ad_config.json`)

SDK ને રન-ટાઇમ પર કંટ્રોલ કરવા માટે તમારા એપના `assets` ફોલ્ડરમાં નીચેની બે ફાઇલો બનાવો:
1. `app/src/main/assets/ad_config.json` (Production માટે)
2. `app/src/main/assets/ad_config_debug.json` (Development/Testing માટે)

**નમૂનાનો JSON ઢાંચો:**
```json
{
  "inter_splash": {
    "id": "ca-app-pub-3940256099942544/1033173712",
    "isEnable": true
  },
  "open_resume": {
    "id": "ca-app-pub-3940256099942544/9257395921",
    "isEnable": true
  },
  "banner_home": {
    "id": "ca-app-pub-3940256099942544/6300978111",
    "isEnable": true
  },
  "inter_details": {
    "id": "ca-app-pub-3940256099942544/1033173712",
    "isEnable": true
  },
  "native_home": {
    "id": "ca-app-pub-3940256099942544/2247696110",
    "isEnable": true
  }
}
```

---

## ૨. SDK પ્રારંભિકરણ (Initialization in Application Class)

તમારી એપ્લિકેશનના ગ્લોબલ `Application` ક્લાસમાં (દા.ત. `DemoApp`) નીચે મુજબ SDK શરૂ કરો:

```kotlin
class DemoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. SDK ઇનિશિયલાઇઝ કરો (isDebug = true રાખવાથી ad_config_debug.json લોડ થશે)
        SMAdManager.initialize(this, isDebug = BuildConfig.DEBUG)

        // 2. એપ ઓપન એડ શરૂ કરો
        SMAppOpenAdManager.getInstance().initialize(this, "open_resume")

        // 3. (મહત્વપૂર્ણ!) સ્પ્લેશ સ્ક્રીન પર એપ ઓપન એડ ન દેખાય તે માટે તેને ડિસેબલ કરો
        SMAppOpenAdManager.getInstance().disableAppOpenForActivity(SplashActivity::class.java)
    }
}
```

---

## ૩. એડ ફોર્મેટ્સનો ઉપયોગ કરવાની રીત

### A. Splash Screen પર ઇન્ટરસ્ટિશિયલ એડ (`inter_splash`)
સ્પ્લેશ સ્ક્રીન પર જાહેરાત લોડ કરીને આગળ જવા માટે `SMAdManager.loadSplashInterstitialAd` નો ઉપયોગ કરો. આમાં સેફ ટાઈમઆઉટ (timeout) સેટ કરી શકાય છે:

```kotlin
SMAdManager.loadSplashInterstitialAd(
    activity = this,
    placementKey = "inter_splash",
    timeoutMs = 8000, // 8 સેકન્ડનો લિમિટ સમય (જો એડ લોડ ન થાય તો એપ ચાલુ થઈ જશે)
    delayMs = 1000,   // એડ ન બતાવવાની હોય તો ડીલે ટાઈમ
    callback = object : SMAdCallback() {
        override fun onNextAction() {
            // જાહેરાત પૂરી થાય એટલે MainActivity પર જાઓ
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
        }
    }
)
```

### B. Adaptive Banner Ad (બેનર જાહેરાત)
સ્ક્રીનની સાઈઝ પ્રમાણે સેટ થતા બેનર લોડ કરવા માટે તમારા લેઆઉટમાં એક `FrameLayout` રાખો અને આ કોડ વાપરો:

```kotlin
SMAdManager.loadBanner(
    activity = this,
    container = binding.bannerAdContainer, // FrameLayout
    placementKey = "banner_home",
    isCollapsible = false // જો કોલેપ્સિબલ બેનર જોઈતું હોય તો true સેટ કરવું
)
```

### C. Interstitial Ad (ફુલ સ્ક્રીન જાહેરાત)
વપરાશકર્તાના સારા અનુભવ માટે ઇન્ટરસ્ટિશિયલ એડ્સને પહેલાથી લોડ (Preload) કરી રાખવી હિતાવહ છે.

1. **Preload કરવા માટે (દા.ત. `onCreate` માં):**
   ```kotlin
   SMAdManager.preloadInterstitialAd(this, "inter_details")
   ```

2. **બતાવવા માટે (દા.ત. બટન ક્લિક પર):**
   ```kotlin
   SMAdManager.showInterstitialAd(
       activity = this,
       placementKey = "inter_details",
       callback = object : SMAdCallback() {
           override fun onAdClosed() {
               // જાહેરાત બંધ થયા પછીની પ્રોસેસ
               goToDetailsScreen()
           }
       }
   )
   ```

### D. Native Ads (નેટિવ જાહેરાતો)
SDK માં બે ડિફોલ્ટ સુંદર નેટિવ ટેમ્પ્લેટ્સ આપેલા છે:
- **Small Native Template**: નાના બોક્સ માટે (આઇકોન, ટાઇટલ, બોડી, બટન)
- **Medium Native Template**: મીડિયા ફાઈલ સાથે (આઇકોન, ટાઇટલ, બોડી, બટન, મોટો ઈમેજ એરિયા)

```kotlin
SMAdManager.loadNativeAd(
    context = this,
    placementKey = "native_home",
    onLoaded = { nativeAd ->
        // ડિફોલ્ટ નાનું ટેમ્પ્લેટ વાપરવા માટે:
        SMNativeAdHelper.populateDefaultSmallNative(this, nativeAd, binding.nativeAdContainer)
        
        // અથવા મીડિયમ ટેમ્પ્લેટ માટે:
        // SMNativeAdHelper.populateDefaultMediumNative(this, nativeAd, binding.nativeAdContainer)
    },
    onFailed = { error ->
        // જાહેરાત નિષ્ફળ જાય ત્યારે લેઆઉટ છુપાવો
        binding.nativeAdContainer.visibility = View.GONE
    }
)
```

---

## ૪. પ્રીમિયમ અથવા ઇન-એપ પર્ચેઝ (Premium User / No Ads)

જ્યારે કોઈ યુઝર Premium ખરીદી કરે ત્યારે બધી એડ્સ એક જ લાઈનથી બંધ કરી શકાય છે:
```kotlin
// true સેટ કરવાથી બધી જ જાહેરાતો (App Open પણ) આપોઆપ બંધ થઈ જશે
SMAdManager.setPremiumUser(true)
```
જો પ્રીમિયમ બંધ કરવું હોય તો `SMAdManager.setPremiumUser(false)` કોલ કરી શકો છો.

---

## ૫. ગૂગલની એડ્સ પોલિસીનું પાલન (Google Policy Compliance)
આ SDK માં નીચે મુજબની પોલિસીઓનું વિશેષ ધ્યાન રાખવામાં આવ્યું છે:
1. **App Open Ad Control**: સ્પ્લેશ કે લૉગિન સ્ક્રીન પર ક્યારેય આકસ્મિક રીતે એડ ન બતાવાય તે માટે `disableAppOpenForActivity` ફંક્શન આપેલું છે.
2. **Frequency Cap**: બે ઇન્ટરસ્ટિશિયલ જાહેરાત વચ્ચે ૩૦ સેકન્ડનો ડિફોલ્ટ સમય રાખવામાં આવ્યો છે, જેથી યુઝરને વારંવાર જાહેરાતો હેરાન ન કરે.
3. **Internet check**: ઈન્ટરનેટ કનેક્શન ન હોય ત્યારે જાહેરાત લોડ કરવાના વધારાના કૉલ્સ રોકવામાં આવે છે, જેથી એપ ક્રેસ ન થાય કે પર્ફોર્મન્સ બગડે નહીં.
