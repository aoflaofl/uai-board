package spamalot.uaiboard.match;

/**
 * The Elo difference estimated from a win/draw/loss record, with its 95% error margin and the likelihood of superiority (the
 * probability that the first player really is the stronger). Same formulas as the gataxx and libataxx match tools, so results
 * can be compared between them.
 *
 * @param score the first player's score, between 0 and 1
 * @param elo the Elo difference (infinite when one side won every game)
 * @param margin the 95% error margin in Elo (infinite when it cannot be estimated)
 * @param los the likelihood of superiority, between 0 and 1
 */
public record Elo(double score, double elo, double margin, double los) {

    /** The estimate for a record seen from the first player's side; with no games it is level and the margin infinite. */
    public static Elo of(int wins, int draws, int losses) {
        int n = wins + draws + losses;
        if (n == 0) {
            return new Elo(0.5, 0, Double.POSITIVE_INFINITY, 0.5);
        }
        double score = (wins + draws / 2.0) / n;
        double variance = (wins * sq(1 - score) + draws * sq(0.5 - score) + losses * sq(score)) / n;
        double standardError = Math.sqrt(variance / n);
        // Delta method: d(elo)/d(score) = 400 / (ln 10 * s * (1 - s)), finite until the score reaches 0 or 1.
        double margin = score <= 0 || score >= 1
                ? Double.POSITIVE_INFINITY
                : 1.96 * standardError * 400 / (Math.log(10) * score * (1 - score));
        double los = wins + losses == 0 ? 0.5 : 0.5 * (1 + erf((wins - losses) / Math.sqrt(2.0 * (wins + losses))));
        return new Elo(score, toElo(score), margin, los);
    }

    /** The Elo difference that corresponds to a score. */
    static double toElo(double score) {
        if (score <= 0) {
            return Double.NEGATIVE_INFINITY;
        }
        if (score >= 1) {
            return Double.POSITIVE_INFINITY;
        }
        return -400 * Math.log10(1 / score - 1);
    }

    private static double sq(double v) {
        return v * v;
    }

    /** The Abramowitz-Stegun approximation 7.1.26 of the error function, accurate to about 1.5e-7. */
    static double erf(double x) {
        double sign = x < 0 ? -1 : 1;
        double ax = Math.abs(x);
        double t = 1 / (1 + 0.3275911 * ax);
        double y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592)
                * t * Math.exp(-ax * ax);
        return sign * y;
    }

    /** {@code +3.5 +/- 19.7} (or {@code inf}) for display. */
    public String format() {
        return String.format("%s +/- %s", number(elo, true), number(margin, false));
    }

    private static String number(double v, boolean signed) {
        if (Double.isInfinite(v)) {
            return v > 0 ? (signed ? "+inf" : "inf") : "-inf";
        }
        return String.format(signed ? "%+.1f" : "%.1f", v);
    }
}
