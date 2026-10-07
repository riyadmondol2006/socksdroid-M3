## Privacy policy for SocksDroid M3

SocksDroid M3 is a generic VPN application that connects to a SOCKS5 server specified by the user.
It contains no analytics, advertising or tracking code and doesn't collect any data for its developers.

### Network connections

* While connected, SocksDroid M3 forwards all or some of the device's network traffic (as configured by the user)
  to the SOCKS5 server specified in the active profile. DNS queries are sent over TCP to the DNS server configured
  in the profile.
* The optional **Test connection** feature connects to the configured SOCKS5 server and, through that server,
  requests `api.ipify.org` once to display the public IP address seen by websites. This only happens when
  the user taps the button.

SocksDroid M3 does not connect to any other service.

### Data stored on the device

Profiles (server addresses, ports, usernames, passwords and app lists) and app settings are stored only in the
app's private storage on the device. Profiles are excluded from cloud backups. Connection logs are kept in memory
only and are discarded when the app process ends. The user can export profiles to a file of their choosing.

### Permissions

* **VPN** – to route traffic through the SOCKS5 server.
* **Query all packages** – only to show the list of installed apps for the per-app proxy feature. The list never
  leaves the device.
* **Notifications** – to show the ongoing connection status.
* **Run at startup** – only used if "Connect on boot" is enabled.

### Trusting the server

As a VPN application SocksDroid M3 forwards network traffic to the server specified,
so it is important that the user trusts this server. In particular if the server is maintained by a third-party
VPN provider, this provider will be able to read or modify data transmitted from any application using non-encrypted
protocols or block access to applications using encrypted protocols.
This is not specific to SocksDroid M3, any VPN application has the same concerns.

SocksDroid M3 is [open source](https://github.com/riyadmondol2006/socksdroid), which makes it easier to review its
security by independent researchers.
