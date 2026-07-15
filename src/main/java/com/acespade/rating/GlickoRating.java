package com.acespade.rating;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Glicko-2 rating state for one player.
 */
@Data
@AllArgsConstructor
public class GlickoRating {
    private double rating;
    private double ratingDeviation;
    private double volatility;

    public GlickoRating copy() {
        return new GlickoRating(rating, ratingDeviation, volatility);
    }
}
