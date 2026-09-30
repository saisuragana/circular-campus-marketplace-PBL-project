import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/ai-assistant")
public class AIAssistantServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String query = request.getParameter("q");

        if (query == null) {
            query = "";
        }

        query = query.trim();

        PrintWriter out = response.getWriter();

        if (query.isEmpty()) {
            out.print(
                    "{\"ok\":true,\"type\":\"help\","
                    + "\"reply\":\"Tell me what you are looking for, "
                    + "for example laptop under 30000 or programming book.\","
                    + "\"products\":[]}"
            );
            return;
        }

        try {

            AISearchEngine.SearchIntent intent =
                    AISearchEngine.understand(query);

            // -------------------------------------------------
            // GREETING
            // -------------------------------------------------

            if (intent.isGreeting()) {

                out.print(
                        "{\"ok\":true,"
                        + "\"type\":\"greeting\","
                        + "\"reply\":\"Hi! I am Campus AI. "
                        + "Tell me what you are looking for, "
                        + "such as laptop under 30000 or programming book.\","
                        + "\"products\":[]}"
                );

                return;
            }

            // -------------------------------------------------
            // HELP REQUEST
            // -------------------------------------------------

            if (intent.isHelpRequest()) {

                String lower = query.toLowerCase();

                String reply;

                if (lower.contains("sell")
                        || lower.contains("listing")
                        || lower.contains("list an item")) {

                    reply =
                            "To sell an item, open Sell an Item, "
                            + "enter the product details, price, "
                            + "condition and quantity, then submit the listing.";

                } else if (lower.contains("cart")
                        || lower.contains("checkout")
                        || lower.contains("buy")) {

                    reply =
                            "To buy an item, open Marketplace, "
                            + "select a product and add it to your cart. "
                            + "Then open Cart and continue to checkout.";

                } else {

                    reply =
                            "I can help you search for products, "
                            + "find items within a budget, "
                            + "and explain how to use the marketplace.";
                }

                out.print(
                        "{\"ok\":true,"
                        + "\"type\":\"help\","
                        + "\"reply\":\""
                        + escapeJson(reply)
                        + "\","
                        + "\"products\":[]}"
                );

                return;
            }

            // -------------------------------------------------
            // DATABASE SEARCH
            // -------------------------------------------------

            List<ProductResult> results =
                    new ArrayList<ProductResult>();

            StringBuilder sql = new StringBuilder();

            sql.append(
                    "SELECT l.listing_id, "
                    + "l.title, "
                    + "l.category, "
                    + "l.price, "
                    + "l.`condition`, "
                    + "l.description, "
                    + "l.keywords, "
                    + "l.quantity "
                    + "FROM listings l "
                    + "WHERE l.status = 'Available' "
                    + "AND l.quantity > 0 "
            );

            List<Object> parameters =
                    new ArrayList<Object>();

            // -------------------------------------------------
            // CATEGORY FILTER
            // -------------------------------------------------

            if (intent.getCategory() != null) {

                sql.append("AND l.category = ? ");

                parameters.add(intent.getCategory());
            }

            // -------------------------------------------------
            // PRICE FILTER
            // -------------------------------------------------

            if (intent.getMaxPrice() != null) {

                sql.append("AND l.price <= ? ");

                parameters.add(intent.getMaxPrice());
            }

            try (Connection connection =
                         DBConnection.getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(sql.toString())) {

                for (int i = 0; i < parameters.size(); i++) {

                    statement.setObject(
                            i + 1,
                            parameters.get(i)
                    );
                }

                try (ResultSet rs =
                             statement.executeQuery()) {

                    while (rs.next()) {

                        int id =
                                rs.getInt("listing_id");

                        String title =
                                rs.getString("title");

                        String category =
                                rs.getString("category");

                        double price =
                                rs.getDouble("price");

                        String condition =
                                rs.getString("condition");

                        String description =
                                rs.getString("description");

                        String keywords =
                                rs.getString("keywords");

                        double score =
                                AISearchEngine.calculateRelevance(
                                        intent,
                                        title,
                                        category,
                                        description,
                                        keywords,
                                        condition,
                                        price
                                );

                        int normalizedScore =
                                AISearchEngine.normalizeScore(score);

                        if (normalizedScore <= 0) {
                            continue;
                        }

                        results.add(
                                new ProductResult(
                                        id,
                                        title,
                                        category,
                                        price,
                                        normalizedScore
                                )
                        );
                    }
                }
            }

            // -------------------------------------------------
            // SORT BY AI RELEVANCE
            // -------------------------------------------------

            Collections.sort(
                    results,
                    new Comparator<ProductResult>() {

                        @Override
                        public int compare(
                                ProductResult a,
                                ProductResult b) {

                            return Integer.compare(
                                    b.score,
                                    a.score
                            );
                        }
                    }
            );

            // -------------------------------------------------
            // LIMIT TO TOP 5
            // -------------------------------------------------

            if (results.size() > 5) {

                results =
                        new ArrayList<ProductResult>(
                                results.subList(0, 5)
                        );
            }

            // -------------------------------------------------
            // RESPONSE MESSAGE
            // -------------------------------------------------

            String reply;

            if (results.isEmpty()) {

                reply =
                        "I couldn't find a matching product. "
                        + "Try a different keyword, category or budget.";

            } else {

                StringBuilder message =
                        new StringBuilder();

                message.append("I found ");
                message.append(results.size());
                message.append(" matching item");

                if (results.size() != 1) {
                    message.append("s");
                }

                if (intent.getCategory() != null) {

                    message.append(" in ");
                    message.append(intent.getCategory());
                }

                if (intent.getMaxPrice() != null) {

                    message.append(" within ₹");
                    message.append(
                            formatPrice(
                                    intent.getMaxPrice()
                            )
                    );
                }

                message.append(".");

                reply = message.toString();
            }

            // -------------------------------------------------
            // JSON OUTPUT
            // -------------------------------------------------

            StringBuilder json =
                    new StringBuilder();

            json.append("{");
            json.append("\"ok\":true,");
            json.append("\"type\":\"search\",");
            json.append("\"reply\":\"");
            json.append(escapeJson(reply));
            json.append("\",");
            json.append("\"products\":[");

            for (int i = 0; i < results.size(); i++) {

                ProductResult product =
                        results.get(i);

                if (i > 0) {
                    json.append(",");
                }

                json.append("{");

                json.append("\"id\":");
                json.append(product.id);
                json.append(",");

                json.append("\"title\":\"");
                json.append(
                        escapeJson(product.title)
                );
                json.append("\",");

                json.append("\"category\":\"");
                json.append(
                        escapeJson(product.category)
                );
                json.append("\",");

                json.append("\"price\":");
                json.append(product.price);
                json.append(",");

                json.append("\"score\":");
                json.append(product.score);

                json.append("}");
            }

            json.append("]");
            json.append("}");

            out.print(json.toString());

        } catch (Exception e) {

            e.printStackTrace();

            response.setStatus(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            );

            out.print(
                    "{\"ok\":false,"
                    + "\"type\":\"error\","
                    + "\"reply\":\"Campus AI encountered a server error.\","
                    + "\"products\":[]}"
            );
        }
    }

    // ---------------------------------------------------------
    // PRODUCT RESULT
    // ---------------------------------------------------------

    private static class ProductResult {

        int id;
        String title;
        String category;
        double price;
        int score;

        ProductResult(
                int id,
                String title,
                String category,
                double price,
                int score) {

            this.id = id;
            this.title = title;
            this.category = category;
            this.price = price;
            this.score = score;
        }
    }

    // ---------------------------------------------------------
    // JSON ESCAPING
    // ---------------------------------------------------------

    private static String escapeJson(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    // ---------------------------------------------------------
    // PRICE FORMATTING
    // ---------------------------------------------------------

    private static String formatPrice(Double price) {

        if (price == null) {
            return "0";
        }

        if (price.doubleValue()
                == Math.floor(price.doubleValue())) {

            return String.valueOf(
                    price.longValue()
            );
        }

        return String.valueOf(price);
    }
}