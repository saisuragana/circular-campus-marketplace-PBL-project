
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Local AI-assisted price estimation engine.
 *
 * Uses current marketplace data rather than an external AI API.
 */
public class AIPriceEngine {

    public static class PriceSuggestion {
        private final BigDecimal suggested;
        private final BigDecimal low;
        private final BigDecimal high;
        private final int samples;
        private final String basis;

        public PriceSuggestion(BigDecimal suggested, BigDecimal low,
                               BigDecimal high, int samples, String basis) {
            this.suggested = suggested;
            this.low = low;
            this.high = high;
            this.samples = samples;
            this.basis = basis;
        }

        public BigDecimal getSuggested() { return suggested; }
        public BigDecimal getLow() { return low; }
        public BigDecimal getHigh() { return high; }
        public int getSamples() { return samples; }
        public String getBasis() { return basis; }
    }

    private AIPriceEngine() {}

    public static PriceSuggestion suggest(
            Connection con, String category, String condition) throws Exception {

        BigDecimal conditionAvg = null;
        BigDecimal categoryAvg = null;
        BigDecimal low = null;
        BigDecimal high = null;
        int conditionSamples = 0;
        int categorySamples = 0;

        String sqlCondition =
                "SELECT AVG(price), MIN(price), MAX(price), COUNT(*) " +
                "FROM listings WHERE status='Available' AND quantity>0 " +
                "AND category=? AND `condition`=?";

        try (PreparedStatement ps = con.prepareStatement(sqlCondition)) {
            ps.setString(1, category);
            ps.setString(2, condition);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    conditionAvg = rs.getBigDecimal(1);
                    low = rs.getBigDecimal(2);
                    high = rs.getBigDecimal(3);
                    conditionSamples = rs.getInt(4);
                }
            }
        }

        String sqlCategory =
                "SELECT AVG(price), MIN(price), MAX(price), COUNT(*) " +
                "FROM listings WHERE status='Available' AND quantity>0 " +
                "AND category=?";

        try (PreparedStatement ps = con.prepareStatement(sqlCategory)) {
            ps.setString(1, category);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    categoryAvg = rs.getBigDecimal(1);
                    if (low == null) low = rs.getBigDecimal(2);
                    if (high == null) high = rs.getBigDecimal(3);
                    categorySamples = rs.getInt(4);
                }
            }
        }

        BigDecimal suggested;

        if (conditionAvg != null && categoryAvg != null) {
            // Give condition-specific history more weight.
            suggested = conditionAvg.multiply(new BigDecimal("0.70"))
                    .add(categoryAvg.multiply(new BigDecimal("0.30")));
        } else if (conditionAvg != null) {
            suggested = conditionAvg;
        } else if (categoryAvg != null) {
            suggested = categoryAvg;
        } else {
            // Fallback only when the marketplace has no historical prices.
            suggested = new BigDecimal("500");
            low = new BigDecimal("300");
            high = new BigDecimal("800");
        }

        suggested = roundToNearest50(suggested);

        if (low != null) low = roundToNearest50(low);
        if (high != null) high = roundToNearest50(high);

        int samples = Math.max(conditionSamples, categorySamples);

        String basis;
        if (conditionSamples > 0) {
            basis = "current campus listings with the same category and condition";
        } else if (categorySamples > 0) {
            basis = "current campus listings in the same category";
        } else {
            basis = "default campus-marketplace estimate; not enough historical data";
        }

        return new PriceSuggestion(suggested, low, high, samples, basis);
    }

    private static BigDecimal roundToNearest50(BigDecimal value) {
        if (value == null) return null;
        return value.divide(new BigDecimal("50"), 0, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("50"))
                .setScale(0, RoundingMode.HALF_UP);
    }
}
