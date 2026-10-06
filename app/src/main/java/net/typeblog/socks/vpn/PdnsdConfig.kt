package net.typeblog.socks.vpn

/**
 * pdnsd configuration: listens on the tun address (where tun2socks' --dnsgw forwards DNS packets),
 * caches answers and resolves upstream over TCP, which works through SOCKS5.
 */
internal fun pdnsdConfig(cacheDir: String, upstreamIp: String, upstreamPort: Int): String = """
    global {
        perm_cache = 1024;
        cache_dir = "$cacheDir";
        server_ip = ${TunConfig.ADDRESS};
        server_port = ${TunConfig.DNS_PORT};
        query_method = tcp_only;
        min_ttl = 15m;
        max_ttl = 1w;
        timeout = 10;
        daemon = off;
    }

    server {
        label = "upstream";
        ip = $upstreamIp;
        port = $upstreamPort;
        uptest = none;
    }

    rr {
        name = localhost;
        reverse = on;
        a = 127.0.0.1;
        owner = localhost;
        soa = localhost, root.localhost, 42, 86400, 900, 86400, 86400;
    }
""".trimIndent() + "\n"
