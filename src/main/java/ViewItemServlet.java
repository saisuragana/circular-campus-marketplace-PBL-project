import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@WebServlet("/view-item")
public class ViewItemServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final String COOKIE_NAME =
            "recentItems";

    private static final int MAX_RECENT = 8;

    private static final int COOKIE_AGE =
            7 * 24 * 60 * 60;

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

        String idParam =
                request.getParameter("id");

        if (idParam == null
                || idParam.trim().isEmpty()) {

            response.sendRedirect("marketplace");
            return;
        }

        int id;

        try {
            id = Integer.parseInt(idParam);
        } catch (NumberFormatException e) {
            response.sendRedirect("marketplace");
            return;
        }

        Integer me =
                CartUtil.studentId(request);

        try (Connection con =
                     DBConnection.getConnection()) {

            String sql =
                    "SELECT l.listing_id, "
                  + "l.seller_id, "
                  + "l.title, "
                  + "l.category, "
                  + "l.price, "
                  + "l.`condition`, "
                  + "l.status, "
                  + "l.description, "
                  + "l.keywords, "
                  + "l.quantity, "
                  + "l.validation_status, "
                  + "l.validation_score, "
                  + "l.validation_reason, "
                  + "l.created_at, "
                  + "s.name AS seller_name, "
                  + "s.verification_status "
                  + "FROM listings l "
                  + "JOIN students s "
                  + "ON s.student_id = l.seller_id "
                  + "WHERE l.listing_id = ?";

            try (PreparedStatement ps =
                         con.prepareStatement(sql)) {

                ps.setInt(1, id);

                try (ResultSet rs =
                             ps.executeQuery()) {

                    if (!rs.next()) {

                        out.print(
                                Page.message(
                                        request,
                                        "Product",
                                        "Product not found",
                                        "This listing may have been removed.",
                                        "marketplace",
                                        "Back to marketplace",
                                        "error"
                                )
                        );

                        return;
                    }

                    int sellerId =
                            rs.getInt("seller_id");

                    boolean ownListing =
                            me != null
                            && me.intValue() == sellerId;

                    String status =
                            rs.getString("status");

                    int quantity =
                            rs.getInt("quantity");

                    boolean buyable =
                            "Available".equalsIgnoreCase(status)
                            && quantity > 0
                            && !ownListing;

                    /*
                     * Do not expose unavailable listings
                     * to other students.
                     */
                    if (!ownListing
                            && !buyable) {

                        out.print(
                                Page.message(
                                        request,
                                        "Product unavailable",
                                        "This product is not currently available",
                                        "The listing may have been sold, "
                                        + "removed, or is no longer available.",
                                        "marketplace",
                                        "Back to marketplace",
                                        "warn"
                                )
                        );

                        return;
                    }

                    /*
                     * Recently viewed cookie.
                     */
                    List<Integer> recent =
                            readRecent(request);

                    recent.remove(
                            Integer.valueOf(id)
                    );

                    recent.add(
                            0,
                            id
                    );

                    while (recent.size()
                            > MAX_RECENT) {

                        recent.remove(
                                recent.size() - 1
                        );
                    }

                    writeRecent(
                            response,
                            recent
                    );

                    out.print(
                            Page.header(
                                    request,
                                    rs.getString("title"),
                                    "marketplace"
                            )
                    );

                    out.print(
                            "<section class=\"product-page\">"
                    );

                    out.print(
                            detail(
                                    con,
                                    rs,
                                    buyable,
                                    ownListing
                            )
                    );

                    out.print(
                            recommendationStrip(
                                    con,
                                    rs,
                                    id
                            )
                    );

                    out.print(
                            recentStrip(
                                    con,
                                    recent,
                                    id
                            )
                    );

                    out.print(
                            "</section>"
                    );

                    out.print(
                            Page.footer()
                    );
                }
            }

        } catch (SQLException e) {

            e.printStackTrace();

            out.print(
                    Page.message(
                            request,
                            "Product",
                            "Unable to load product",
                            "Check MySQL in XAMPP and try again.",
                            "marketplace",
                            "Back to marketplace",
                            "error"
                    )
            );
        }
    }

    private String detail(
            Connection con,
            ResultSet rs,
            boolean buyable,
            boolean ownListing)
            throws SQLException {

        int listingId =
                rs.getInt("listing_id");

        String title =
                rs.getString("title");

        String category =
                rs.getString("category");

        String condition =
                rs.getString("condition");

        String status =
                rs.getString("status");

        String description =
                rs.getString("description");

        String keywords =
                rs.getString("keywords");

        String validation =
                rs.getString("validation_status");

        int validationScore =
                rs.getInt("validation_score");

        String validationReason =
                rs.getString("validation_reason");

        BigDecimal price =
                rs.getBigDecimal("price");

        int quantity =
                rs.getInt("quantity");

        StringBuilder sb =
                new StringBuilder();

        /*
         * Main product container.
         */
        sb.append(
                "<div class=\"product-detail glass\">"
        );

        /*
         * LEFT SIDE - IMAGE GALLERY
         */
        sb.append(
                "<div class=\"product-gallery\">"
        );

        sb.append(
                imageGallery(
                        con,
                        listingId,
                        category
                )
        );

        sb.append(
                "</div>"
        );

        /*
         * RIGHT SIDE - PRODUCT INFORMATION
         */
        sb.append(
                "<div class=\"product-info\">"
        );

        /*
         * Category
         */
        sb.append(
                "<p class=\"muted\">"
        );

        sb.append(
                Page.esc(category)
        );

        sb.append(
                "</p>"
        );

        /*
         * Title
         */
        sb.append(
                "<h1>"
        );

        sb.append(
                Page.esc(title)
        );

        sb.append(
                "</h1>"
        );

        /*
         * Price
         */
        sb.append(
                "<p class=\"product-price\">"
                + "&#8377;"
        );

        sb.append(
                Page.money(price)
        );

        sb.append(
                "</p>"
        );

        /*
         * Status chips.
         */
        sb.append(
                "<div class=\"chips\">"
        );

        sb.append(
                "<span class=\"chip\">"
        );

        sb.append(
                Page.esc(condition)
        );

        sb.append(
                "</span>"
        );

        sb.append(
                "<span class=\"chip\">"
        );

        sb.append(
                Page.esc(status)
        );

        sb.append(
                "</span>"
        );

        if (validation != null
                && !validation.trim().isEmpty()) {

            sb.append(
                    "<span class=\"chip\">"
            );

            sb.append(
                    Page.esc(validation)
            );

            sb.append(
                    "</span>"
            );
        }

        sb.append(
                "</div>"
        );

        /*
         * Automated validation information.
         */
        if (validationScore > 0
                || (validation != null
                && !validation.trim().isEmpty())) {

            sb.append(
                    "<div class=\"glass\" "
                    + "style=\"padding:.75rem;"
                    + "margin:1rem 0;\">"
            );

            sb.append(
                    "<strong>"
                    + "Automated Product Check"
                    + "</strong>"
            );

            sb.append(
                    "<p class=\"muted\" "
                    + "style=\"margin:.3rem 0 0;\">"
            );

            if (validationScore > 0) {

                sb.append(
                        "Quality score: "
                );

                sb.append(
                        validationScore
                );

                sb.append(
                        "/100"
                );
            } else {

                sb.append(
                        "Listing information and uploaded "
                        + "product images were automatically checked."
                );
            }

            if (validationReason != null
                    && !validationReason.trim().isEmpty()) {

                sb.append(
                        "<br>"
                );

                sb.append(
                        Page.esc(
                                validationReason
                        )
                );
            }

            sb.append(
                    "</p>"
            );

            sb.append(
                    "</div>"
            );
        }

        /*
         * Description.
         */
        sb.append(
                "<div class=\"product-description\">"
                + "<h3>Description</h3>"
        );

        sb.append(
                "<p>"
        );

        if (description == null
                || description.trim().isEmpty()) {

            sb.append(
                    "No description provided."
            );

        } else {

            sb.append(
                    Page.esc(description)
            );
        }

        sb.append(
                "</p>"
        );

        sb.append(
                "</div>"
        );

        /*
         * Product specifications.
         */
        sb.append(
                "<div class=\"product-specs\">"
        );

        sb.append(
                "<p>"
                + "<strong>Seller:</strong> "
        );

        sb.append(
                Page.esc(
                        rs.getString(
                                "seller_name"
                        )
                )
        );

        sb.append(
                "</p>"
        );

        sb.append(
                "<p>"
                + "<strong>Available:</strong> "
        );

        sb.append(
                quantity
        );

        sb.append(
                "</p>"
        );

        String sellerVerification =
                rs.getString(
                        "verification_status"
                );

        if (sellerVerification != null
                && !sellerVerification.trim().isEmpty()) {

            sb.append(
                    "<p>"
                    + "<strong>Seller verification:</strong> "
            );

            sb.append(
                    Page.esc(
                            sellerVerification
                    )
            );

            sb.append(
                    "</p>"
            );
        }

        sb.append(
                "</div>"
        );

        /*
         * Keywords.
         */
        if (keywords != null
                && !keywords.trim().isEmpty()) {

            sb.append(
                    "<div class=\"chips\">"
            );

            String[] parts =
                    keywords.split(",");

            for (String keyword : parts) {

                if (keyword != null
                        && !keyword.trim().isEmpty()) {

                    sb.append(
                            "<span class=\"chip\">"
                    );

                    sb.append(
                            Page.esc(
                                    keyword.trim()
                            )
                    );

                    sb.append(
                            "</span>"
                    );
                }
            }

            sb.append(
                    "</div>"
            );
        }

        /*
         * Actions.
         */
        sb.append(
                "<div class=\"product-actions\">"
        );

        if (ownListing) {

            sb.append(
                    "<span class=\"muted\">"
                    + "This is your listing."
                    + "</span>"
            );

        } else if (buyable) {

            sb.append(
                    "<a class=\"btn btn-primary\" "
                    + "href=\"add-to-cart?id="
            );

            sb.append(
                    listingId
            );

            sb.append(
                    "\">"
                    + "Add to Cart"
                    + "</a>"
            );

            sb.append(
                    "<a class=\"btn btn-ghost\" "
                    + "href=\"cart?buyNow="
            );

            sb.append(
                    listingId
            );

            sb.append(
                    "\">"
                    + "Buy Now"
                    + "</a>"
            );

        } else {

            sb.append(
                    "<span class=\"muted\">"
                    + "Currently unavailable."
                    + "</span>"
            );
        }

        sb.append(
                "</div>"
        );

        sb.append(
                "</div>"
        );

        sb.append(
                "</div>"
        );

        return sb.toString();
    }

    private String imageGallery(
            Connection con,
            int listingId,
            String category)
            throws SQLException {

        StringBuilder sb =
                new StringBuilder();

        String sql =
                "SELECT image_id, file_name "
              + "FROM listing_images "
              + "WHERE listing_id = ? "
              + "ORDER BY image_id ASC";

        int count = 0;

        try (PreparedStatement ps =
                     con.prepareStatement(sql)) {

            ps.setInt(
                    1,
                    listingId
            );

            try (ResultSet rs =
                         ps.executeQuery()) {

                while (rs.next()) {

                    count++;

                    String fileName =
                            rs.getString(
                                    "file_name"
                            );

                    sb.append(
                            "<div class=\"product-image-card\">"
                    );

                    sb.append(
                            "<img src=\"product-image?file="
                    );

                    try {

                        sb.append(
                                java.net.URLEncoder.encode(
                                        fileName,
                                        "UTF-8"
                                )
                        );

                    } catch (
                            java.io.UnsupportedEncodingException e) {

                        sb.append(
                                fileName
                        );
                    }

                    sb.append(
                            "\" alt=\""
                    );

                    sb.append(
                            Page.esc(
                                    category
                            )
                    );

                    sb.append(
                            " product image\" "
                            + "loading=\"lazy\">"
                    );

                    sb.append(
                            "</div>"
                    );
                }
            }
        }

        /*
         * Fallback if no image exists.
         */
        if (count == 0) {

            sb.append(
                    "<div class=\"product-image-placeholder\">"
            );

            sb.append(
                    "<div class=\"category-icon\">"
            );

            sb.append(
                    Icons.svg(
                            categoryIcon(category)
                    )
            );

            sb.append(
                    "</div>"
            );

            sb.append(
                    "<p>No product images uploaded.</p>"
            );

            sb.append(
                    "</div>"
            );
        }

        return sb.toString();
    }

    private String categoryIcon(
            String category) {

        if (category == null) {
            return "box";
        }

        if ("Books".equalsIgnoreCase(
                category
        )) {
            return "book";
        }

        if ("Electronics".equalsIgnoreCase(
                category
        )) {
            return "cpu";
        }

        if ("Sports".equalsIgnoreCase(
                category
        )) {
            return "activity";
        }

        if ("Accessories".equalsIgnoreCase(
                category
        )) {
            return "shopping-bag";
        }

        return "box";
    }

    /*
     * Similar products.
     *
     * This intentionally uses a direct SQL query instead
     * of calling AIRecommendationEngine because that class's
     * Product fields are private and its recommend() method
     * has a different signature.
     */
    private String recommendationStrip(
            Connection con,
            ResultSet current,
            int id)
            throws SQLException {

        String category =
                current.getString(
                        "category"
                );

        StringBuilder sb =
                new StringBuilder();

        String sql =
                "SELECT listing_id, "
              + "title, "
              + "category, "
              + "price, "
              + "`condition` "
              + "FROM listings "
              + "WHERE status = 'Available' "
              + "AND quantity > 0 "
              + "AND listing_id <> ? "
              + "AND category = ? "
              + "ORDER BY created_at DESC "
              + "LIMIT 4";

        try (PreparedStatement ps =
                     con.prepareStatement(sql)) {

            ps.setInt(
                    1,
                    id
            );

            ps.setString(
                    2,
                    category
            );

            try (ResultSet rs =
                         ps.executeQuery()) {

                boolean found = false;

                while (rs.next()) {

                    if (!found) {

                        found = true;

                        sb.append(
                                "<div class=\"glass\" "
                                + "style=\"padding:1.25rem;"
                                + "margin-top:1rem;\">"
                        );

                        sb.append(
                                "<h2>Similar Products</h2>"
                        );

                        sb.append(
                                "<div class=\"market-grid\">"
                        );
                    }

                    sb.append(
                            "<a class=\"market-card\" "
                            + "href=\"view-item?id="
                    );

                    sb.append(
                            rs.getInt(
                                    "listing_id"
                            )
                    );

                    sb.append(
                            "\">"
                    );

                    sb.append(
                            "<div class=\"market-card-body\">"
                    );

                    sb.append(
                            "<p class=\"muted\">"
                    );

                    sb.append(
                            Page.esc(
                                    rs.getString(
                                            "category"
                                    )
                            )
                    );

                    sb.append(
                            "</p>"
                    );

                    sb.append(
                            "<h3>"
                    );

                    sb.append(
                            Page.esc(
                                    rs.getString(
                                            "title"
                                    )
                            )
                    );

                    sb.append(
                            "</h3>"
                    );

                    sb.append(
                            "<p>&#8377;"
                    );

                    sb.append(
                            Page.money(
                                    rs.getBigDecimal(
                                            "price"
                                    )
                            )
                    );

                    sb.append(
                            "</p>"
                    );

                    sb.append(
                            "<span class=\"chip\">"
                    );

                    sb.append(
                            Page.esc(
                                    rs.getString(
                                            "condition"
                                    )
                            )
                    );

                    sb.append(
                            "</span>"
                    );

                    sb.append(
                            "</div>"
                    );

                    sb.append(
                            "</a>"
                    );
                }

                if (found) {

                    sb.append(
                            "</div>"
                    );

                    sb.append(
                            "</div>"
                    );
                }
            }
        }

        return sb.toString();
    }

    private String recentStrip(
            Connection con,
            List<Integer> recent,
            int currentId)
            throws SQLException {

        if (recent == null
                || recent.isEmpty()) {

            return "";
        }

        StringBuilder sb =
                new StringBuilder();

        sb.append(
                "<div class=\"glass\" "
                + "style=\"padding:1.25rem;"
                + "margin-top:1rem;\">"
        );

        sb.append(
                "<h2>Recently Viewed</h2>"
        );

        sb.append(
                "<div class=\"chips\">"
        );

        boolean found = false;

        /*
         * We show actual product names instead of
         * only "Product #ID".
         */
        String sql =
                "SELECT listing_id, title "
              + "FROM listings "
              + "WHERE listing_id IN (";

        List<Integer> filtered =
                new ArrayList<Integer>();

        for (Integer itemId : recent) {

            if (itemId != null
                    && itemId.intValue() != currentId) {

                filtered.add(
                        itemId
                );
            }
        }

        if (filtered.isEmpty()) {
            return "";
        }

        for (int i = 0;
             i < filtered.size();
             i++) {

            if (i > 0) {
                sql += ",";
            }

            sql += "?";
        }

        sql += ")";

        try (PreparedStatement ps =
                     con.prepareStatement(sql)) {

            for (int i = 0;
                 i < filtered.size();
                 i++) {

                ps.setInt(
                        i + 1,
                        filtered.get(i)
                );
            }

            try (ResultSet rs =
                         ps.executeQuery()) {

                while (rs.next()) {

                    found = true;

                    sb.append(
                            "<a class=\"chip\" "
                            + "href=\"view-item?id="
                    );

                    sb.append(
                            rs.getInt(
                                    "listing_id"
                            )
                    );

                    sb.append(
                            "\">"
                    );

                    sb.append(
                            Page.esc(
                                    rs.getString(
                                            "title"
                                    )
                            )
                    );

                    sb.append(
                            "</a>"
                    );
                }
            }
        }

        sb.append(
                "</div>"
        );

        sb.append(
                "</div>"
        );

        if (!found) {
            return "";
        }

        return sb.toString();
    }

    private List<Integer> readRecent(
            HttpServletRequest request) {

        List<Integer> ids =
                new LinkedList<Integer>();

        Cookie[] cookies =
                request.getCookies();

        if (cookies == null) {
            return ids;
        }

        for (Cookie cookie : cookies) {

            if (!COOKIE_NAME.equals(
                    cookie.getName()
            )) {
                continue;
            }

            String value =
                    cookie.getValue();

            if (value == null
                    || value.trim().isEmpty()) {

                return ids;
            }

            String[] parts =
                    value.split("-");

            for (String part : parts) {

                try {

                    int recentId =
                            Integer.parseInt(
                                    part
                            );

                    if (recentId > 0
                            && !ids.contains(
                                    recentId
                            )) {

                        ids.add(
                                recentId
                        );
                    }

                } catch (
                        NumberFormatException ignored) {
                }

                if (ids.size()
                        >= MAX_RECENT) {

                    break;
                }
            }

            break;
        }

        return ids;
    }

    private void writeRecent(
            HttpServletResponse response,
            List<Integer> ids) {

        StringBuilder value =
                new StringBuilder();

        for (Integer id : ids) {

            if (id == null) {
                continue;
            }

            if (value.length() > 0) {
                value.append("-");
            }

            value.append(
                    id
            );
        }

        Cookie cookie =
                new Cookie(
                        COOKIE_NAME,
                        value.toString()
                );

        cookie.setMaxAge(
                COOKIE_AGE
        );

        cookie.setPath(
                "/"
        );

        response.addCookie(
                cookie
        );
    }
}