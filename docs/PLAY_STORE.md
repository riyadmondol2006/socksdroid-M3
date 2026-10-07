# Publishing SocksDroid M3 on Google Play

Checklist and suggested Play Console answers, based on Google Play policies as of October 2026. Policies change, so
check the linked pages before submitting.

## Read first: two policy risks

1. **VPN apps need an organization account.** Play Console Requirements: *"developers providing the following
   services must register as an Organization: … Apps approved to use the VpnService class."*
   An organization account needs a free [D-U-N-S number](https://www.dnb.com/duns/get-a-duns.html).
   Sources: [answer/10788890](https://support.google.com/googleplay/android-developer/answer/10788890),
   [answer/13634885](https://support.google.com/googleplay/android-developer/answer/13634885)
2. **The VpnService policy asks for encryption.** It says apps *"must encrypt the data from the device to VPN tunnel
   end point"*. SOCKS5 itself is not encrypted. Other plain SOCKS5 clients that use VpnService are on Play, so this
   isn't always enforced, but review can reject the app for it. Be honest about it in the declaration. The app and
   listing already state that SOCKS5 doesn't encrypt traffic.
   Source: [answer/9888170](https://support.google.com/googleplay/android-developer/answer/9888170) (VPN Service)

## 1. Build and signing

- Upload the **App Bundle**: `SocksDroid-M3-<version>-release.aab` from a GitHub release, or
  `./gradlew bundleRelease` (`app/build/outputs/bundle/release/app-release.aab`). Play only accepts bundles.
- Sign it with your own key (see the README). With **Play App Signing**, that key becomes your *upload key*, and
  Google signs the APKs that users install.
- `versionCode` is the git commit count, so it always goes up. Don't rewrite or squash `master`'s history, or
  `versionCode` could go backwards and Play would reject the upload.

## 2. App content (Policy → App content)

| Section | Answer |
|---|---|
| Privacy policy | `https://github.com/riyadmondol2006/socksdroid/blob/master/PRIVACY.md` |
| Ads | No, the app doesn't contain ads |
| App access | Some functionality is restricted. Give reviewers a working SOCKS5 server: host, port, username and password, plus "Tap the power button, accept the notice and Android's VPN dialog." Without a server, reviewers can't test the app. |
| Content rating | Fill in the IARC questionnaire: utility app, no user-generated content, no sharing of location, no purchases |
| Target audience | 18 and over |
| News app / Government / Financial / Health | No |
| Data safety | See below |
| VPN service | See below |
| Foreground service | See below |

### Data safety

- **Does your app collect or share any of the required user data types?** No.
  - User traffic goes only to the server the user configures, and the developer never receives it. Google defines
    "collect" as transmitting data off the device *to the developer or third parties*; user-initiated transfers
    to the user's own destination are excluded.
  - Profiles, settings and logs stay on the device.
  - The installed-app list is processed on the device only.
- **Is all user data encrypted in transit?** Answer **No**. Don't claim encryption, because SOCKS5 is plaintext.
- **Can users request deletion?** Data is only on the device: delete profiles, clear storage, or uninstall.

Source: [answer/10787469](https://support.google.com/googleplay/android-developer/answer/10787469)

### VPN service declaration

1. **Is providing a VPN the core functionality?** Yes. The app routes device traffic through a SOCKS5 server the
   user configures.
2. **Video (90 s max) of the VPN in use.** Open the app, tap the power button, accept the notice and the system
   dialog, show "Connected" and the key icon, then browse a site.
3. **Data collected or shared via the VPN:** none.
4. **Video of the prominent disclosure.** Show all of these:
   - On a fresh install, tap connect and the notice "How SocksDroid M3 uses the VPN" appears.
   - **Decline**: the VPN doesn't start.
   - Tap connect again and the notice comes back.
   - **Accept**: Android's VPN dialog appears, then the app connects.
   - The notice can be viewed again in Settings → About → *VPN service notice*.
5. **Monetization or ad traffic redirection:** No.

Source: [answer/12564964](https://support.google.com/googleplay/android-developer/answer/12564964)

Record videos from a connected phone with:

```sh
adb shell screenrecord --time-limit 90 /sdcard/vpn-demo.mp4
adb pull /sdcard/vpn-demo.mp4
```

Upload them to YouTube as *unlisted* and paste the links.

### Foreground service

- The VPN runs as a `systemExempted` foreground service. That's the type Android documents for VPN apps, and Play's
  foreground-service policy exempts it.
- If the console still asks for a declaration, use:
  - **Description:** "Keeps the user-started VPN tunnel to the user's SOCKS5 server running and shows its status
    with a Disconnect button."
  - **Impact if interrupted:** "All proxied apps lose their network connection until the user reconnects."

Source: [answer/13392821](https://support.google.com/googleplay/android-developer/answer/13392821)

## 3. Store listing

Everything is in [`fastlane/metadata/android/en-US`](../fastlane/metadata/android/en-US):

| Field | File |
|---|---|
| App name (30 max) | `title.txt` |
| Short description (80 max) | `short_description.txt` |
| Full description (4000 max) | `full_description.txt` |
| App icon 512×512 | `images/icon.png` |
| Feature graphic 1024×500 | `images/featureGraphic.png` |
| Phone screenshots (2–8) | `images/phoneScreenshots/*.png` |

- **Category:** Tools.
- **Contact details:** an email address is required, and a website is optional (the GitHub repo works).

### App name (13 / 30)

```
SocksDroid M3
```

### Short description (69 / 80)

```
Route all apps or selected apps through your own SOCKS5 proxy server.
```

### Full description (2,009 / 4,000)

```
SocksDroid M3 connects your device to a SOCKS5 proxy server that you run or have access to. It uses Android's VpnService (the system VPN feature) to route the traffic of all apps, or only the apps you choose, through that server. Before the first connection the app explains how the VPN is used and asks for your consent.

It is a client only: it does not include or sell any proxy server. You enter the address of your own SOCKS5 server.

Features
• Route all apps, or only selected apps, through the proxy. You can also choose apps that bypass it.
• Optional username and password authentication.
• DNS through the proxy, so DNS lookups don't go directly to your local network.
• Bypass LAN keeps local network addresses reachable directly.
• IPv6 is forwarded through the proxy or blocked, never sent around it.
• UDP forwarding when the server runs badvpn-udpgw.
• Multiple profiles: share and import them as socks5:// links, or import and export them as files.
• Test connection checks the server and shows the latency and the public IP address seen through it.
• Quick Settings tile and a notification with a Disconnect button.
• Works with Android's Always-on VPN and "Block connections without VPN" settings.
• Optional connect on boot and automatic reconnect.
• Material You design with dynamic colors, light and dark themes and a pure-black option.
• Logs screen for troubleshooting.

Important
• SOCKS5 does not encrypt your traffic. Use it with servers you trust, and rely on encrypted protocols such as HTTPS for sensitive data.
• The operator of the server you connect to can see and modify unencrypted traffic that passes through it.

Privacy
SocksDroid M3 has no ads, no analytics and no account. It does not collect any data. Profiles are stored only on your device.

Open source
SocksDroid M3 is free software under the GNU GPL v3. It is an independent fork of SocksDroid by PeterCxy and isn't affiliated with the original authors. Source code: https://github.com/riyadmondol2006/socksdroid
```

The `fastlane` files above are the source for these texts. If you change the listing, update both.

### Translations

`fastlane/metadata/android/<language>/` also holds translated listings for 12 languages: ar, bn-BD, de-DE, es-419,
fr-FR, hi-IN, id, pt-BR, ru-RU, tr-TR, vi and zh-CN. Each says that the app's interface is in English.

To add them in Play Console, go to **Grow users → Store presence → Store listings → Manage translations →
Import translations with AI** and upload a single file with every language. To build that file:

```sh
for d in fastlane/metadata/android/*/; do
  [ "$(basename "$d")" = en-US ] && continue
  printf '===== %s =====\n\nApp name:\n%s\nShort description:\n%s\nFull description:\n%s\n\n' \
    "$(basename "$d")" "$(cat "$d/title.txt")" "$(cat "$d/short_description.txt")" "$(cat "$d/full_description.txt")"
done > store-translations.txt
```

Check the imported text in each language before you save. Translated listings use the en-US graphics unless you
upload others.

## 4. Testing and release

- **Personal accounts** created after Nov 13, 2023 must run a closed test with at least **12 testers for 14
  days** before production. Organization accounts are exempt.
  Source: [answer/14151465](https://support.google.com/googleplay/android-developer/answer/14151465)
- **Developer verification (2026):** creating the app in Play Console registers the package name
  `com.riyadm.socksdroid`.
  - With Play App Signing, the APKs on GitHub releases are signed with your key, not Google's. Register that key
    as well in Play Console → *Android developer verification*, so GitHub installs stay valid when verification
    applies globally in 2027.
  - Source: [developer.android.com/developer-verification](https://developer.android.com/developer-verification)
- Start with **Internal testing**, then **Closed testing**, then **Production**.

## 5. Name

- "Droid" is a Lucasfilm trademark, and Google's brand guidelines forbid names "confusingly similar to Android".
  Many "-droid" apps are live, but a trademark complaint could force a rename after launch.
- Choosing a name without "Droid" before the first release avoids the risk.
- The listing already says this is an independent fork, not affiliated with the original SocksDroid.
