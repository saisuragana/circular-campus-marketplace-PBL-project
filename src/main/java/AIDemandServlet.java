
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * GET /ai-demand
 *
 * Local demand estimation using marketplace listings and transactions.
 * This is a transparent heuristic, not a trained ML forecasting model.
 */
@WebServlet("/ai-demand")
public class AIDemandServlet extends HttpServlet {

    private static class Demand {
        String category;
        int activeListings;
        int soldQuantity;
        double score;
        String level;

        Demand(String category, int activeListings,
               int soldQuantity, double score, String level) {
            this.category = category;
            this.activeListings = activeListings;
            this.soldQuantity = soldQuantity;
            this.score = score;
            this.level = level;
        }
    }

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json;charset=UTF-8");

        List<Demand> data = new ArrayList<Demand>();

        String sql =
                "SELECT l.category, " +
                "COUNT(DISTINCT CASE WHEN l.status='Available' AND l.quantity>0 " +
                "THEN l.listing_id END) AS active_listings, " +
                "COALESCE(SUM(t.quantity),0) AS sold_quantity " +
                "FROM listings l " +
                "LEFT JOIN transactions t ON t.listing_id=l.listing_id " +
                "GROUP BY l.category";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                String category = rs.getString("category");
                int listings = rs.getInt("active_listings");
                int sold = rs.getInt("sold_quantity");

                /*
                 * Demand heuristic:
                 * - More sold units increases demand.
                 * - Selling activity relative to current supply increases demand.
                 * - Capped at 100 for a simple demo-friendly index.
                 */
                double score =
                        Math.min(
                                100,
                                sold * 12.0 +
                                (listings == 0
                                        ? sold * 10.0
                                        : ((double) sold / listings) * 45.0)
                        );

                String level;

                if (score >= 70) {
                    level = "High";
                } else if (score >= 35) {
                    level = "Medium";
                } else {
                    level = "Low";
                }

                data.add(
                        new Demand(
                                category,
                                listings,
                                sold,
                                score,
                                level
                        )
                );
            }

        } catch (Exception e) {

            PrintWriter out = response.getWriter();

            out.print(
                    "{\"ok\":false,\"message\":\"Demand data unavailable right now.\"}"
            );

            return;
        }

        Collections.sort(
                data,
                new Comparator<Demand>() {
                    @Override
                    public int compare(Demand a, Demand b) {
                        return Double.compare(b.score, a.score);
                    }
                }
        );

        StringBuilder json =
                new StringBuilder();

        json.append(
                "{\"ok\":true,\"categories\":["
        );

        for (int i = 0; i < data.size(); i++) {

            if (i > 0) {
                json.append(",");
            }

            Demand d = data.get(i);

            json.append("{")
                .append("\"category\":\"")
                .append(escape(d.category))
                .append("\",")
                .append("\"activeListings\":")
                .append(d.activeListings)
                .append(",")
                .append("\"soldQuantity\":")
                .append(d.soldQuantity)
                .append(",")
                .append("\"score\":")
                .append(Math.round(d.score))
                .append(",")
                .append("\"level\":\"")
                .append(d.level)
                .append("\"")
                .append("}");
        }

        json.append("]}");

        response.getWriter().print(
                json.toString()
        );
    }

    private String escape(String value) {
        if (value == null) return "";

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
