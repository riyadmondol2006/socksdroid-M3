## Privacy policy for SocksDroid M3

This privacy policy applies to the Android app **SocksDroid M3** (package `com.riyadm.socksdroid`).

SocksDroid M3 is a SOCKS5 proxy client. It uses Android's VPN service (`VpnService`) to route network traffic to a
SOCKS5 server that the user specifies. It contains no analytics, advertising or tracking code, has no user accounts,
and the developer does not collect, receive, store or share any personal or sensitive user data.

### How the VPN service is used

* While connected, SocksDroid M3 forwards the network traffic of all apps, or only the apps the user selects, to the
  SOCKS5 server configured in the active profile. The traffic goes only to that server; it is never sent to the
  developer or to any other party chosen by the app.
* By default, DNS queries are also sent through that server (over TCP) to the DNS server set in the profile.
* SOCKS5 does not encrypt traffic. The operator of the configured server can see and modify traffic that is not
  already encrypted by the apps themselves (for example, traffic that does not use HTTPS).
* The app does not inspect, log, modify or monetize the content of the traffic it forwards, and does not redirect
  advertising traffic.

### Other network connections

The optional **Test connection** feature, run only when the user taps it, connects to the configured SOCKS5 server
and, through it, makes one HTTPS request to `api.ipify.org` to display the public IP address seen through the
server. SocksDroid M3 does not connect to any other service.

### Data stored on the device

* **Profiles** (server addresses, ports, usernames, passwords, DNS settings and per-app lists) and **app settings**
  are stored only in the app's private storage on the device. Profiles are excluded from cloud backups.
* **Logs** of the VPN service are kept in memory only and are discarded when the app stops. The user can copy or
  share them manually from the Logs screen.
* The list of installed apps shown in the per-app proxy picker is read on the device and never leaves it.
* The user can export profiles to a file of their choosing; the app never uploads them.

### Data retention and deletion

All data stays on the device until the user deletes it. Deleting a profile removes it immediately; clearing the
app's storage or uninstalling the app removes all profiles, settings and logs. Because the developer receives no
data, there is no data held by the developer to retain or delete.

### Permissions

* **VPN service** – to route traffic through the SOCKS5 server, only after the user accepts the in-app notice and
  Android's VPN consent dialog.
* **Internet and network state** – to connect to the SOCKS5 server.
* **Local network access** (Android 17 and later) – requested only when the SOCKS5 or DNS server is on the local
  network.
* **Notifications** and **foreground service** – to show the ongoing connection status with a Disconnect button.
* **Run at startup** – only used if the user enables "Connect on boot".

### Children

SocksDroid M3 is not directed at children.

### Changes to this policy

Changes are published in this file in the app's public source repository, with the full history of revisions.

### Contact

SocksDroid M3 is developed by riyadmondol2006. For privacy questions or requests, open an issue at
<https://github.com/riyadmondol2006/socksdroid/issues>.

SocksDroid M3 is [open source](https://github.com/riyadmondol2006/socksdroid), which makes it easier for
independent researchers to review its security. It is an independent fork of SocksDroid by PeterCxy and is not
affiliated with the original authors.
