import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.*;

@WebServlet("/my-listings")
public class MyListingsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        response.setContentType(
                "text/html;charset=UTF-8"
        );

        PrintWriter out =
                response.getWriter();

        Integer me =
                CartUtil.studentId(request);

        if (me == null) {

            out.print(
                    Page.message(
                            request,
                            "My Listings",
                            "Log in to manage your listings",
                            "Your products are linked to your student account.",
                            "login.html",
                            "Log in",
                            "warn"
                    )
            );

            return;
        }

        try (Connection con =
                     DBConnection.getConnection()) {

            out.print(
                    Page.header(
                            request,
                            "My Listings",
                            "my-listings"
                    )
            );

            out.print(
                    "<section class=\"txn-page\">"
            );

            out.print(
                    "<div style=\"display:flex;"
                    + "justify-content:space-between;"
                    + "align-items:center;"
                    + "gap:1rem;"
                    + "flex-wrap:wrap;"
                    + "margin-bottom:1.5rem;\">"
                    + "<div>"
                    + "<h1>My Listings</h1>"
                    + "<p class=\"muted\">"
                    + "Manage the products you have listed "
                    + "on the campus marketplace."
                    + "</p>"
                    + "</div>"
                    + "<a class=\"btn btn-primary\" "
                    + "href=\"create-listing.html\">"
                    + "Sell New Item"
                    + "</a>"
                    + "</div>"
            );

            String sql =
                    "SELECT l.listing_id, "
                  + "l.title, "
                  + "l.category, "
                  + "l.price, "
                  + "l.`condition`, "
                  + "l.status, "
                  + "l.description, "
                  + "l.quantity, "
                  + "l.validation_status, "
                  + "l.validation_score, "
                  + "l.validation_reason, "
                  + "l.created_at, "
                  + "COUNT(li.image_id) AS image_count "
                  + "FROM listings l "
                  + "LEFT JOIN listing_images li "
                  + "ON li.listing_id = l.listing_id "
                  + "WHERE l.seller_id = ? "
                  + "GROUP BY l.listing_id, "
                  + "l.title, "
                  + "l.category, "
                  + "l.price, "
                  + "l.`condition`, "
                  + "l.status, "
                  + "l.description, "
                  + "l.quantity, "
                  + "l.validation_status, "
                  + "l.validation_score, "
                  + "l.validation_reason, "
                  + "l.created_at "
                  + "ORDER BY l.created_at DESC";

            try (PreparedStatement ps =
                         con.prepareStatement(sql)) {

                ps.setInt(1, me);

                try (ResultSet rs =
                             ps.executeQuery()) {

                    boolean found = false;

                    while (rs.next()) {

                        found = true;

                        out.print(
                                listingCard(
                                        rs
                                )
                        );
                    }

                    if (!found) {

                        out.print(
                                "<div class=\"txn-block glass\">"
                                + "<h2>No listings yet</h2>"
                                + "<p class=\"muted\">"
                                + "You haven't listed anything yet. "
                                + "Create your first listing to start "
                                + "selling to other students."
                                + "</p>"
                                + "<a class=\"btn btn-primary\" "
                                + "href=\"create-listing.html\">"
                                + "Create Your First Listing"
                                + "</a>"
                                + "</div>"
                        );
                    }
                }
            }

            out.print("</section>");

            out.print(
                    Page.footer()
            );

        } catch (SQLException e) {

            System.err.println(
                    "[MyListingsServlet] "
                    + e.getMessage()
            );

            e.printStackTrace();

            out.print(
                    Page.message(
                            request,
                            "My Listings",
                            "Unable to load your listings",
                            "Check MySQL in XAMPP and try again.",
                            "marketplace",
                            "Back to marketplace",
                            "error"
                    )
            );
        }
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        Integer me =
                CartUtil.studentId(request);

        if (me == null) {
            response.sendRedirect("login.html");
            return;
        }

        String action =
                request.getParameter("action");

        if (!"remove".equalsIgnoreCase(action)) {

            response.sendRedirect(
                    "my-listings"
            );

            return;
        }

        int listingId;

        try {

            listingId =
                    Integer.parseInt(
                            request.getParameter(
                                    "listingId"
                            )
                    );

        } catch (Exception e) {

            response.sendRedirect(
                    "my-listings"
            );

            return;
        }

        try (Connection con =
                     DBConnection.getConnection()) {

            /*
             * Seller can remove only their own listing.
             */
            String sql =
                    "UPDATE listings "
                  + "SET status = 'Removed' "
                  + "WHERE listing_id = ? "
                  + "AND seller_id = ?";

            try (PreparedStatement ps =
                         con.prepareStatement(sql)) {

                ps.setInt(1, listingId);
                ps.setInt(2, me);

                int updated =
                        ps.executeUpdate();

                if (updated > 0) {

                    request.getSession()
                            .setAttribute(
                                    "flash",
                                    "Listing removed successfully."
                            );
                }
            }

            response.sendRedirect(
                    "my-listings"
            );

        } catch (SQLException e) {

            e.printStackTrace();

            response.sendRedirect(
                    "my-listings"
            );
        }
    }

    private String listingCard(
            ResultSet rs)
            throws SQLException {

        int listingId =
                rs.getInt("listing_id");

        String title =
                rs.getString("title");

        String category =
                rs.getString("category");

        BigDecimal price =
                rs.getBigDecimal("price");

        String condition =
                rs.getString("condition");

        String status =
                rs.getString("status");

        String validation =
                rs.getString("validation_status");

        int score =
                rs.getInt("validation_score");

        String reason =
                rs.getString("validation_reason");

        int imageCount =
                rs.getInt("image_count");

        int quantity =
                rs.getInt("quantity");

        if (validation == null
                || validation.trim().isEmpty()) {

            validation =
                    "Auto Checked";
        }

        String validationClass =
                validationClass(
                        validation
                );

        StringBuilder sb =
                new StringBuilder();

        sb.append(
                "<div class=\"txn-block glass\" "
                + "style=\"margin-bottom:1rem;\">"
        );

        /*
         * Header
         */
        sb.append(
                "<div style=\"display:flex;"
                + "justify-content:space-between;"
                + "align-items:flex-start;"
                + "gap:1rem;"
                + "flex-wrap:wrap;\">"
        );

        sb.append(
                "<div>"
                + "<p class=\"muted\" "
                + "style=\"margin-bottom:.25rem;\">"
        )
        .append(
                Page.esc(category)
        )
        .append("</p>");

        sb.append(
                "<h2 style=\"margin:0;\">"
        )
        .append(
                Page.esc(title)
        )
        .append("</h2>");

        sb.append("</div>");

        sb.append(
                "<div style=\"text-align:right;\">"
                + "<div style=\"font-size:1.4rem;"
                + "font-weight:700;\">"
                + "&#8377;"
        )
        .append(
                Page.money(price)
        )
        .append("</div>"
                + "</div>"
        );

        sb.append("</div>");

        /*
         * Status chips
         */
        sb.append(
                "<div class=\"chips\" "
                + "style=\"margin-top:.8rem;\">"
        );

        sb.append(
                "<span class=\"chip\">"
        )
        .append(
                Page.esc(condition)
        )
        .append("</span>");

        sb.append(
                "<span class=\"chip\">"
        )
        .append(
                Page.esc(status)
        )
        .append("</span>");

        sb.append(
                "<span class=\"chip "
        )
        .append(validationClass)
        .append("\">"
        )
        .append(
                Page.esc(validation)
        )
        .append("</span>");

        sb.append("</div>");

        /*
         * Automated validation information
         */
        sb.append(
                "<div class=\"glass\" "
                + "style=\"padding:.85rem;"
                + "margin-top:1rem;\">"
        );

        sb.append(
                "<strong>"
                + "Automated Product Check"
                + "</strong>"
        );

        sb.append(
                "<p class=\"muted\" "
                + "style=\"margin:.35rem 0 0;\">"
                + "Images uploaded: "
        )
        .append(imageCount)
        .append(" &middot; "
                + "Quantity: "
        )
        .append(quantity);

        if (score > 0) {

            sb.append(
                    " &middot; Quality score: "
            )
            .append(score)
            .append("/100");
        }

        sb.append("</p>");

        if (reason != null
                && !reason.trim().isEmpty()) {

            sb.append(
                    "<p class=\"muted\" "
                    + "style=\"margin:.35rem 0 0;\">"
            )
            .append(
                    Page.esc(reason)
            )
            .append("</p>");
        }

        sb.append("</div>");

        /*
         * Actions
         */
        sb.append(
                "<div style=\"display:flex;"
                + "gap:.6rem;"
                + "flex-wrap:wrap;"
                + "margin-top:1rem;\">"
        );

        sb.append(
                "<a class=\"btn btn-primary btn-sm\" "
                + "href=\"view-item?id="
        )
        .append(listingId)
        .append("\">"
                + "View Product"
                + "</a>"
        );

        /*
         * Only allow removal if it is not already removed.
         */
        if (!"Removed".equalsIgnoreCase(status)) {

            sb.append(
                    "<form method=\"post\" "
                    + "action=\"my-listings\" "
                    + "style=\"display:inline;\" "
                    + "onsubmit=\"return confirm("
                    + "'Remove this listing?'"
                    + ");\">"
            );

            sb.append(
                    "<input type=\"hidden\" "
                    + "name=\"action\" "
                    + "value=\"remove\">"
            );

            sb.append(
                    "<input type=\"hidden\" "
                    + "name=\"listingId\" "
                    + "value=\""
            )
            .append(listingId)
            .append("\">");

            sb.append(
                    "<button class=\"btn btn-ghost btn-sm\" "
                    + "type=\"submit\">"
                    + "Remove Listing"
                    + "</button>"
            );

            sb.append("</form>");
        }

        sb.append("</div>");

        sb.append("</div>");

        return sb.toString();
    }

    private String validationClass(
            String validation) {

        if (validation == null) {
            return "";
        }

        if ("Auto Checked".equalsIgnoreCase(
                validation
        )) {
            return "chip-success";
        }

        if ("Rejected".equalsIgnoreCase(
                validation
        )) {
            return "chip-danger";
        }

        return "";
    }
}