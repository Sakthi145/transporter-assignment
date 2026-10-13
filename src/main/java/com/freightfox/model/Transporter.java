package com.freightfox.model;

import java.util.List;

/**
 * A transporter together with the quotes it has given. A transporter can only serve lanes it has quoted.
 *
 * @param id         unique transporter identifier
 * @param name       display name
 * @param laneQuotes quotes given by this transporter (stored as an unmodifiable copy)
 */
public record Transporter(long id, String name, List<LaneQuote> laneQuotes) {

    public Transporter {
        laneQuotes = List.copyOf(laneQuotes);
    }
}
