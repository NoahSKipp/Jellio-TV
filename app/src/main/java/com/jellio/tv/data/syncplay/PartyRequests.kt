package com.jellio.tv.data.syncplay

const val PARTY_REQUEST_FRESH_MS = 60_000L

// The first poll after joining (or restarting) returns the party's whole
// request history; only a recent ask still deserves the host's answer.
fun isFreshPartyRequest(at: String?, serverNow: String?, windowMs: Long = PARTY_REQUEST_FRESH_MS): Boolean {
    val askedAt = PartyManager.parseTime(at) ?: return true
    val now = PartyManager.parseTime(serverNow) ?: return true
    return now - askedAt <= windowMs
}
