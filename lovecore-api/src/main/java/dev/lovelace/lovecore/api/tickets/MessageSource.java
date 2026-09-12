package dev.lovelace.lovecore.api.tickets;

/**
 * Where a ticket message originated - lets a relay skip echoing a message back to the place it
 * came from (e.g. LoveAuth's Discord relay ignores {@link #DISCORD}-sourced messages it's told
 * about, since those already exist on the Discord side).
 */
public enum MessageSource {
    /** Submitted through LoveWebAdmin's panel (the public appeal form, or a staff reply). */
    PANEL,
    /** Relayed in from a Discord message in the ticket's channel/thread. */
    DISCORD,
    /** Plugin-generated (status changes, etc.), not authored by a person. */
    SYSTEM
}
