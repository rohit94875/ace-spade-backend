package com.acespade.rating;

import java.util.ArrayList;
import java.util.List;

/**
 * Glicko-2 calculator for multi-player free-for-all results.
 * Each finishing rank is converted into pairwise win/loss outcomes vs all opponents.
 */
public final class Glicko2Calculator {

    private static final double TAU = 0.5;
    private static final double EPSILON = 0.000001;

    private Glicko2Calculator() {}

    public static List<GlickoRating> updateRatings(List<GlickoRating> players, List<Integer> ranks) {
        if (players.size() != ranks.size() || players.size() < 2) {
            throw new IllegalArgumentException("Need matching players and ranks (min 2)");
        }

        int n = players.size();
        List<GlickoRating> updated = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            updated.add(updatePlayer(players, ranks, i));
        }
        return updated;
    }

    private static GlickoRating updatePlayer(List<GlickoRating> players, List<Integer> ranks, int idx) {
        GlickoRating player = players.get(idx);
        double mu = toMu(player.getRating());
        double phi = toPhi(player.getRatingDeviation());

        List<Double> muJs = new ArrayList<>();
        List<Double> phiJs = new ArrayList<>();
        List<Double> scoreJs = new ArrayList<>();

        for (int j = 0; j < players.size(); j++) {
            if (j == idx) continue;
            muJs.add(toMu(players.get(j).getRating()));
            phiJs.add(toPhi(players.get(j).getRatingDeviation()));
            scoreJs.add(ranks.get(idx) < ranks.get(j) ? 1.0 : (ranks.get(idx).equals(ranks.get(j)) ? 0.5 : 0.0));
        }

        double vInv = 0;
        double deltaSum = 0;
        for (int k = 0; k < muJs.size(); k++) {
            double gPhi = g(phiJs.get(k));
            double eVal = E(mu, muJs.get(k), phiJs.get(k));
            vInv += gPhi * gPhi * eVal * (1 - eVal);
            deltaSum += gPhi * (scoreJs.get(k) - eVal);
        }
        if (vInv == 0) {
            return player.copy();
        }
        double v = 1.0 / vInv;

        double sigmaPrime = computeSigmaPrime(phi, player.getVolatility(), deltaSum, v);
        double phiStar = Math.sqrt(phi * phi + sigmaPrime * sigmaPrime);
        double phiPrime = 1.0 / Math.sqrt(1.0 / (phiStar * phiStar) + 1.0 / v);
        double muPrime = mu + phiPrime * phiPrime * deltaSum;

        return new GlickoRating(fromMu(muPrime), fromPhi(phiPrime), sigmaPrime);
    }

    private static double computeSigmaPrime(double phi, double sigma, double deltaSum, double v) {
        double delta = v * deltaSum;
        double a = Math.log(sigma * sigma);
        double A = a;
        double deltaSq = delta * delta;
        double phiSq = phi * phi;

        double B;
        if (deltaSq > phiSq + v) {
            B = Math.log(deltaSq - phiSq - v);
        } else {
            int k = 1;
            while (sigmaFunction(a - k * TAU, a, phiSq, v, deltaSq) < 0) {
                k++;
            }
            B = a - k * TAU;
        }

        double fA = sigmaFunction(A, a, phiSq, v, deltaSq);
        double fB = sigmaFunction(B, a, phiSq, v, deltaSq);
        while (Math.abs(B - A) > EPSILON) {
            double C = A + (A - B) * fA / (fB - fA);
            double fC = sigmaFunction(C, a, phiSq, v, deltaSq);
            if (fC * fB < 0) {
                A = B;
                fA = fB;
            } else {
                fA = fA / 2;
            }
            B = C;
            fB = fC;
        }
        return Math.exp(A / 2.0);
    }

    private static double sigmaFunction(double x, double a, double phiSq, double v, double deltaSq) {
        double ex = Math.exp(x);
        double num = ex * (deltaSq - phiSq - v - ex);
        double den = 2 * Math.pow(phiSq + v + ex, 2);
        return num / den - (x - a) / (TAU * TAU);
    }

    private static double g(double phi) {
        return 1.0 / Math.sqrt(1.0 + 3.0 * phi * phi / (Math.PI * Math.PI));
    }

    private static double E(double mu, double muJ, double phiJ) {
        return 1.0 / (1.0 + Math.exp(-g(phiJ) * (mu - muJ)));
    }

    private static double toMu(double rating) {
        return (rating - 1500.0) / 173.7178;
    }

    private static double fromMu(double mu) {
        return 173.7178 * mu + 1500.0;
    }

    private static double toPhi(double rd) {
        return rd / 173.7178;
    }

    private static double fromPhi(double phi) {
        return 173.7178 * phi;
    }
}
