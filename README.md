SocksDroid M3
=============

A SOCKS5 VPN client for Android 8.0+ built on Android's `VpnService`, with a Material 3 (Material You) interface.

This fork is a full rewrite of [SocksDroid by PeterCxy](https://github.com/PeterCxy/SocksDroid), via the
[maintained fork by bndeff](https://github.com/bndeff/socksdroid). All app code is now Kotlin with Jetpack
Compose. It targets the latest Android release (API 37) and supports 16 KB memory pages.

Package name: `com.riyadm.socksdroid`. It installs as a separate app from the original SocksDroid
(`net.typeblog.socks`).

## Features

**Connection**
- Routes all device traffic, or only selected apps, through any SOCKS5 server, with optional username/password
  authentication.
- **DNS through the proxy** (on by default). DNS queries are tunnelled over TCP via the SOCKS5 server, so they
  don't leak to the local network. The DNS answers are cached on the device.
- **No IPv6 leaks.** IPv6 is either forwarded through the proxy or blocked, so it never bypasses the tunnel.
- **Bypass LAN:** local and private networks (192.168.x.x, 10.x.x.x, …) stay reachable directly.
- Route modes: all traffic, or everything except mainland-China IP ranges.
- UDP forwarding via [badvpn-udpgw](https://github.com/ambrop72/badvpn) running on the server.
- **Auto-reconnect** if the tunnel process stops unexpectedly.
- **Always-on VPN** and the system kill switch ("Block connections without VPN") are supported.
- Connect on boot.

**Profiles**
- Multiple profiles: create, rename, duplicate, delete and switch, even while connected.
- **Test connection:** checks the SOCKS5 handshake and credentials, then shows latency and the exit IP address.
- Share a profile as a `socks5://user:pass@host:port#name` link, with or without the password. Open such a link,
  or paste it from the clipboard, to import it.
- Import and export all profiles as JSON.
- **Per-app proxy** with a searchable app picker: proxy only the selected apps, or bypass them.

**Interface and system integration**
- **Quick Settings tile** that toggles the VPN and shows the active profile.
- Ongoing notification with a connection timer and a **Disconnect** action.
- **Material 3 with dynamic color** from your wallpaper (Android 12+), plus System, Light and Dark themes and an
  optional pure-black dark mode.
- Edge-to-edge layout, predictive back, a themed monochrome launcher icon (Android 13+), and a navigation rail on
  tablets and foldables.
- In-app **Logs** screen for the VPN service, tun2socks and pdnsd, with copy, share and clear.

## What changed in this fork

| | SocksDroid 1.x | SocksDroid M3 |
|---|---|---|
| Package | `net.typeblog.socks` | `com.riyadm.socksdroid` |
| Language | Java | Kotlin |
| UI | Framework `PreferenceFragment` | Jetpack Compose, Material 3, dynamic color |
| Target SDK | 31 | 37 (Android 17) |
| Min SDK | 21 | 26 (Android 8.0) |
| Build | Groovy, AGP 7 | Kotlin DSL, version catalog, AGP 9, Gradle 9 |
| Native code | JNI helper + executables, 4 KB aligned | Executables only, 16 KB page aligned, NDK r27 |
| DNS | Sent directly, bypassing the proxy | Tunnelled through the proxy (configurable) |
| IPv6 when disabled | Bypassed the VPN | Blocked |
| Connecting | Blocked the main thread | Runs in coroutines, with auto-reconnect |
| Always-on VPN | Crashed (missing extras) | Supported |
| Credentials with spaces | Broke the command line | Passed as separate process arguments |

## Requirements

- Android 8.0 (API 26) or newer, on arm64-v8a, armeabi-v7a, x86 or x86_64.
- A SOCKS5 server. For UDP forwarding, the server also needs `badvpn-udpgw`.

## Building

You need JDK 17 or newer (with `javac`) and the Android SDK with platform 37.

```sh
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # minified with R8
```

The debug build uses the application ID `com.riyadm.socksdroid.debug`, so it can be installed next to a release
build.

To sign release builds, create `keystore.properties` in the project root. It is git-ignored.

```properties
storeFile=/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

### Native binaries

`tun2socks` (from badvpn) and `pdnsd` are prebuilt in `app/src/main/jniLibs`. They are named `lib*.so` so that
Android extracts them to the app's native library directory, where they can be executed. To rebuild them for all
ABIs with NDK r27:

```sh
app/src/main/build-jni.sh    # uses $ANDROID_NDK_HOME or ~/Android/Sdk/ndk/27.0.12077973
```

The script fails if any output is not 16 KB page aligned.

## How it works

```
apps ──► tun0 (VpnService) ──► tun2socks ──► SOCKS5 server ──► internet
             │ DNS to 8.8.8.8:53
             └──► pdnsd (cache, 26.26.26.1:8091) ──► loopback relay ──► SOCKS5 ──► DNS server (TCP)
```

- `vpn/SocksVpnService.kt` sets up the tun interface and supervises the two native daemons. It passes the tun file
  descriptor to tun2socks over a Unix socket using `LocalSocket`.
- The app excludes itself from its own VPN, so tun2socks and the DNS relay can reach the SOCKS5 server directly.
- `data/` stores profiles and settings in SharedPreferences.
- `ui/` contains the Compose screens: Home, Profiles, Editor, App picker, Settings and Logs.

## Privacy

There is no analytics, advertising or tracking. See [PRIVACY.md](PRIVACY.md).

## Credits

- [PeterCxy](https://github.com/PeterCxy/SocksDroid): original SocksDroid
- [bndeff](https://github.com/bndeff/socksdroid): maintained the upstream fork
- [badvpn](https://github.com/ambrop72/badvpn) (tun2socks) and [pdnsd](http://members.home.nl/p.a.rombouts/pdnsd/)

## License

[GNU General Public License v3.0](LICENSE)
