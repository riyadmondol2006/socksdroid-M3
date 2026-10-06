package net.typeblog.socks.vpn

/** An IPv4 network in CIDR notation, normalized so that host bits are zero. */
internal data class Ipv4Cidr(val network: Long, val prefix: Int) {
    init {
        require(prefix in 0..32) { "Invalid prefix length $prefix" }
        require(network and hostMask(prefix) == 0L) { "Host bits set in network address" }
    }

    private val size: Long get() = 1L shl (32 - prefix)
    private val last: Long get() = network + size - 1

    /** Dotted-quad network address, e.g. `10.0.0.0`. */
    val address: String
        get() = (24 downTo 0 step 8).joinToString(".") { ((network shr it) and 0xFF).toString() }

    operator fun contains(other: Ipv4Cidr): Boolean = other.network >= network && other.last <= last

    fun overlaps(other: Ipv4Cidr): Boolean = network <= other.last && other.network <= last

    /**
     * Returns the smallest set of CIDR blocks covering this network minus [other].
     * Two CIDR blocks are either disjoint or nested, so if they overlap and this one is not
     * fully covered, [other] lies strictly inside it and halving this block converges.
     */
    operator fun minus(other: Ipv4Cidr): List<Ipv4Cidr> = when {
        !overlaps(other) -> listOf(this)
        this in other -> emptyList()
        else -> {
            val half = size / 2
            listOf(Ipv4Cidr(network, prefix + 1), Ipv4Cidr(network + half, prefix + 1)).flatMap { it - other }
        }
    }

    override fun toString(): String = "$address/$prefix"

    companion object {
        private fun hostMask(prefix: Int): Long = (1L shl (32 - prefix)) - 1

        /** Parses `a.b.c.d/n`, clearing any host bits. Returns null for malformed input. */
        fun parse(text: String): Ipv4Cidr? {
            val parts = text.trim().split('/')
            if (parts.size != 2) return null
            val prefix = parts[1].toIntOrNull()?.takeIf { it in 0..32 } ?: return null
            val octets = parts[0].split('.').map { it.toIntOrNull()?.takeIf { o -> o in 0..255 } ?: return null }
            if (octets.size != 4) return null
            val value = octets.fold(0L) { acc, octet -> (acc shl 8) or octet.toLong() }
            return Ipv4Cidr(value and hostMask(prefix).inv() and 0xFFFFFFFFL, prefix)
        }
    }
}

/** Removes every address in [excluded] from these networks. */
internal fun List<Ipv4Cidr>.excluding(excluded: List<Ipv4Cidr>): List<Ipv4Cidr> =
    excluded.fold(this) { remaining, cut -> remaining.flatMap { it - cut } }
