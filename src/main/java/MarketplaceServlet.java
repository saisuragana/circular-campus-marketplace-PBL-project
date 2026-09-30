import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * GET /marketplace
 *
 * Main Circular Campus marketplace.
 *
 * Features:
 * - MySQL-backed listings
 * - Real uploaded product images
 * - AI-assisted natural-language search
 * - AI relevance ranking
 * - Category filtering
 * - Condition filtering
 * - Maximum-price filtering
 * - Manual sorting
 * - Add-to-cart
 * - Live marketplace statistics
 */
@WebServlet("/marketplace")
public class MarketplaceServlet extends HttpServlet {

    /* ----------------------------------------------------------
     * AVAILABLE FILTER VALUES
     * ---------------------------------------------------------- */

    static final String[] CATEGORIES = {
            "Books",
            "Electronics",
            "Hostel Essentials",
            "Sports",
            "Accessories",
            "Other"
    };

    static final String[] CONDITIONS = {
            "New",
            "Like New",
            "Good",
            "Fair"
    };

    /* ----------------------------------------------------------
     * GET /marketplace
     * ---------------------------------------------------------- */

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        request.setCharacterEncoding("UTF-8");

        response.setContentType(
                "text/html;charset=UTF-8"
        );

        /* ------------------------------------------------------
         * READ PARAMETERS
         * ------------------------------------------------------ */

        String q =
                param(request, "q");

        String category =
                param(request, "category");

        String condition =
                param(request, "condition");

        String maxPrice =
                param(request, "maxPrice");

        String sort =
                param(request, "sort");

        PrintWriter out =
                response.getWriter();

        try (
                Connection con =
                        DBConnection.getConnection()
        ) {

            /* --------------------------------------------------
             * AI SEARCH INTENT
             * -------------------------------------------------- */

            AISearchEngine.SearchIntent searchIntent =
                    null;

            if (!q.isEmpty()) {

                searchIntent =
                        AISearchEngine.understand(q);
            }

            /* --------------------------------------------------
             * BASE SQL
             *
             * IMPORTANT:
             * image_file retrieves the first uploaded image
             * for every listing.
             * -------------------------------------------------- */

            StringBuilder sql =
                    new StringBuilder(

                            "SELECT " +
                            "l.listing_id, " +
                            "l.title, " +
                            "l.category, " +
                            "l.price, " +
                            "l.`condition`, " +
                            "l.status, " +
                            "l.description, " +
                            "l.keywords, " +
                            "l.quantity, " +
                            "l.created_at, " +
                            "s.name AS seller_name, " +

                            "(SELECT li.file_name " +
                            " FROM listing_images li " +
                            " WHERE li.listing_id = l.listing_id " +
                            " ORDER BY li.image_id ASC " +
                            " LIMIT 1) AS image_file " +

                            "FROM listings l " +

                            "JOIN students s " +
                            "ON s.student_id = l.seller_id " +

                            "WHERE l.status = 'Available' " +
                            "AND l.quantity > 0"
                    );

            List<Object> params =
                    new ArrayList<Object>();

            /* --------------------------------------------------
             * AI CATEGORY
             * -------------------------------------------------- */

            if (searchIntent != null) {

                if (searchIntent.getCategory() != null) {

                    sql.append(
                            " AND l.category = ?"
                    );

                    params.add(
                            searchIntent.getCategory()
                    );
                }

                /* ----------------------------------------------
                 * AI PRICE
                 * ---------------------------------------------- */

                if (searchIntent.getMaxPrice() != null) {

                    sql.append(
                            " AND l.price <= ?"
                    );

                    params.add(
                            searchIntent.getMaxPrice()
                    );
                }

                /* ----------------------------------------------
                 * AI SEARCH TERMS
                 * ---------------------------------------------- */

                StringBuilder searchConditions =
                        new StringBuilder();

                for (
                        String term :
                        searchIntent.getTerms()
                ) {

                    if (
                            term == null ||
                            term.length() < 2
                    ) {
                        continue;
                    }

                    if (
                            searchConditions.length() > 0
                    ) {

                        searchConditions.append(
                                " OR "
                        );
                    }

                    searchConditions.append(
                            "(l.title LIKE ? " +
                            "OR l.description LIKE ? " +
                            "OR l.keywords LIKE ?)"
                    );

                    String like =
                            "%" + term + "%";

                    params.add(like);
                    params.add(like);
                    params.add(like);
                }

                if (
                        searchConditions.length() > 0
                ) {

                    sql.append(
                            " AND ("
                    )

                    .append(
                            searchConditions
                    )

                    .append(
                            ")"
                    );
                }
            }

            /* --------------------------------------------------
             * NORMAL CATEGORY FILTER
             * -------------------------------------------------- */

            if (!category.isEmpty()) {

                sql.append(
                        " AND l.category = ?"
                );

                params.add(category);
            }

            /* --------------------------------------------------
             * NORMAL CONDITION FILTER
             * -------------------------------------------------- */

            if (!condition.isEmpty()) {

                sql.append(
                        " AND l.`condition` = ?"
                );

                params.add(condition);
            }

            /* --------------------------------------------------
             * NORMAL PRICE FILTER
             * -------------------------------------------------- */

            BigDecimal maxP =
                    null;

            if (!maxPrice.isEmpty()) {

                try {

                    maxP =
                            new BigDecimal(
                                    maxPrice
                            );

                    if (
                            maxP.signum() > 0
                    ) {

                        sql.append(
                                " AND l.price <= ?"
                        );

                        params.add(maxP);
                    }

                } catch (
                        NumberFormatException ignored
                ) {

                    maxP = null;
                }
            }

            /* --------------------------------------------------
             * SORTING
             * -------------------------------------------------- */

            if (
                    "price-low".equals(sort)
            ) {

                sql.append(
                        " ORDER BY l.price ASC"
                );

            } else if (
                    "price-high".equals(sort)
            ) {

                sql.append(
                        " ORDER BY l.price DESC"
                );

            } else if (
                    "title".equals(sort)
            ) {

                sql.append(
                        " ORDER BY l.title ASC"
                );

            } else {

                sql.append(
                        " ORDER BY " +
                        "l.created_at DESC, " +
                        "l.listing_id DESC"
                );
            }

            /* --------------------------------------------------
             * LIVE STATISTICS
             * -------------------------------------------------- */

            int totalAvailable =
                    scalar(
                            con,
                            "SELECT COUNT(*) " +
                            "FROM listings " +
                            "WHERE status = 'Available' " +
                            "AND quantity > 0"
                    );

            int totalSellers =
                    scalar(
                            con,
                            "SELECT COUNT(DISTINCT seller_id) " +
                            "FROM listings"
                    );

            int totalSold =
                    scalar(
                            con,
                            "SELECT COUNT(*) " +
                            "FROM transactions"
                    );

            /* --------------------------------------------------
             * PAGE HEADER
             * -------------------------------------------------- */

            out.print(
                    Page.header(
                            request,
                            "Marketplace",
                            "marketplace"
                    )
            );

            out.print(
                    flash(request)
            );

            out.print(
                    hero(
                            totalAvailable,
                            totalSellers,
                            totalSold
                    )
            );

            out.print(
                    searchPanel(
                            q,
                            category,
                            condition,
                            maxPrice,
                            sort
                    )
            );

            out.print(
                    categoryRow(category)
            );

            /* --------------------------------------------------
             * RESULTS
             * -------------------------------------------------- */

            out.print(
                    "<section class=\"results\" " +
                    "id=\"results\">\n"
            );

            try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    sql.toString()
                            )
            ) {

                /* ----------------------------------------------
                 * BIND SQL PARAMETERS
                 * ---------------------------------------------- */

                for (
                        int i = 0;
                        i < params.size();
                        i++
                ) {

                    Object p =
                            params.get(i);

                    if (
                            p instanceof BigDecimal
                    ) {

                        ps.setBigDecimal(
                                i + 1,
                                (BigDecimal) p
                        );

                    } else if (
                            p instanceof Double
                    ) {

                        ps.setDouble(
                                i + 1,
                                (Double) p
                        );

                    } else {

                        ps.setString(
                                i + 1,
                                String.valueOf(p)
                        );
                    }
                }

                List<ScoredCard> cards =
                        new ArrayList<ScoredCard>();

                /* ----------------------------------------------
                 * LOAD PRODUCTS
                 * ---------------------------------------------- */

                try (
                        ResultSet rs =
                                ps.executeQuery()
                ) {

                    while (rs.next()) {

                        String html =
                                card(rs);

                        double score =
                                0;

                        /* --------------------------------------
                         * AI RELEVANCE
                         * -------------------------------------- */

                        if (
                                searchIntent != null
                        ) {

                            score =
                                    AISearchEngine
                                            .calculateRelevance(

                                                    searchIntent,

                                                    rs.getString(
                                                            "title"
                                                    ),

                                                    rs.getString(
                                                            "category"
                                                    ),

                                                    rs.getString(
                                                            "keywords"
                                                    ),

                                                    rs.getString(
                                                            "description"
                                                    ),

                                                    rs.getString(
                                                            "condition"
                                                    ),

                                                    rs.getBigDecimal(
                                                            "price"
                                                    ).doubleValue()
                                            );
                        }

                        cards.add(
                                new ScoredCard(
                                        score,
                                        html
                                )
                        );
                    }
                }

                /* ----------------------------------------------
                 * AI RANKING
                 *
                 * Only when:
                 * - search is being performed
                 * - manual sorting was not selected
                 * ---------------------------------------------- */

                if (
                        !q.isEmpty() &&
                        sort.isEmpty()
                ) {

                    cards.sort(
                            (a, b) ->
                                    Double.compare(
                                            b.score,
                                            a.score
                                    )
                    );
                }

                /* ----------------------------------------------
                 * RESULTS HEADER
                 * ---------------------------------------------- */

                out.print(
                        "<div class=\"results-head\">\n" +

                        "<h2>" +

                        (
                                q.isEmpty() &&
                                category.isEmpty()

                                ? "Everything on campus right now"

                                : "Search results"
                        )

                        + "</h2>\n"

                        + "<p class=\"results-count\">"

                        + cards.size()

                        + (
                                cards.size() == 1
                                        ? " listing"
                                        : " listings"
                        )

                        + (
                                q.isEmpty()
                                        ? ""
                                        :
                                " matching &ldquo;"
                                + Page.esc(q)
                                + "&rdquo;"
                        )

                        + "</p>\n"

                        + "</div>\n"
                );

                /* ----------------------------------------------
                 * EMPTY STATE
                 * ---------------------------------------------- */

                if (
                        cards.isEmpty()
                ) {

                    out.print(

                            "<div class=\"empty glass\">\n"

                            + "<h3>" +
                            "Nothing matches those filters yet" +
                            "</h3>\n"

                            + "<p>" +
                            "Clear the filters, or be the first " +
                            "to list something like this." +
                            "</p>\n"

                            + "<div class=\"empty-actions\">"

                            + "<a class=\"btn btn-ghost\" " +
                            "href=\"marketplace\">" +
                            "Clear filters" +
                            "</a>"

                            + "<a class=\"btn btn-primary\" " +
                            "href=\"create-listing.html\">" +
                            "Sell an item" +
                            "</a>"

                            + "</div>\n"

                            + "</div>\n"
                    );

                } else {

                    /* ------------------------------------------
                     * PRODUCT GRID
                     * ------------------------------------------ */

                    out.print(
                            "<div class=\"grid\">\n"
                    );

                    for (
                            ScoredCard c :
                            cards
                    ) {

                        out.print(
                                c.html
                        );
                    }

                    out.print(
                            "</div>\n"
                    );
                }
            }

            out.print(
                    "</section>\n"
            );

            out.print(
                    Page.footer()
            );

        } catch (SQLException e) {

            System.err.println(
                    "[MarketplaceServlet] "
                    + e.getMessage()
            );

            out.print(
                    Page.message(

                            request,

                            "Database unavailable",

                            "The marketplace cannot reach the database",

                            "Open the XAMPP control panel, " +
                            "start MySQL, then reload this page.",

                            "marketplace",

                            "Try again",

                            "error"
                    )
            );
        }
    }

    /* ==========================================================
     * FLASH MESSAGE
     * ========================================================== */

    private String flash(
            HttpServletRequest request) {

        String msg =
                request.getParameter("msg");

        if (
                msg != null &&
                !msg.trim().isEmpty()
        ) {

            return
                    "<div class=\"flash glass\">"
                    + Page.esc(msg)
                    + "</div>\n";
        }

        HttpSession session =
                request.getSession(false);

        if (session == null) {
            return "";
        }

        Object flash =
                session.getAttribute("flash");

        if (flash == null) {
            return "";
        }

        session.removeAttribute(
                "flash"
        );

        return
                "<div class=\"flash glass\">"
                + Page.esc(
                        flash.toString()
                )
                + "</div>\n";
    }

    /* ==========================================================
     * HERO
     * ========================================================== */

    private String hero(
            int listings,
            int sellers,
            int sold) {

        return

                "<section class=\"hero\">\n"

                + "  <div class=\"hero-copy\">\n"

                + "    <h1 class=\"hero-title\">"
                + "Your campus.<br>"
                + "Your marketplace."
                + "</h1>\n"

                + "    <p class=\"hero-sub\">"
                + "Sell the textbook you finished, "
                + "buy the calculator you need, "
                + "and keep it all inside the campus. "
                + "Pay from a wallet that never leaves "
                + "the college."
                + "</p>\n"

                + "    <div class=\"hero-actions\">\n"

                + "      <a class=\"btn btn-primary\" "
                + "href=\"#results\">"
                + "Browse marketplace"
                + "</a>\n"

                + "      <a class=\"btn btn-ghost\" "
                + "href=\"create-listing.html\">"
                + "Sell an item"
                + "</a>\n"

                + "    </div>\n"

                + "    <dl class=\"stats\">\n"

                + "      <div>"
                + "<dt>Listed now</dt>"
                + "<dd>"
                + listings
                + "</dd>"
                + "</div>\n"

                + "      <div>"
                + "<dt>Students selling</dt>"
                + "<dd>"
                + sellers
                + "</dd>"
                + "</div>\n"

                + "      <div>"
                + "<dt>Items resold</dt>"
                + "<dd>"
                + sold
                + "</dd>"
                + "</div>\n"

                + "    </dl>\n"

                + "    <p class=\"stats-note\">"
                + "Counted live from the marketplace database."
                + "</p>\n"

                + "  </div>\n"

                + "  <div class=\"hero-stage\" "
                + "id=\"heroStage\" "
                + "aria-hidden=\"true\">\n"

                + "    <div class=\"orbit\">\n"

                + floatObject("book", 0)
                + floatObject("laptop", 1)
                + floatObject("headphones", 2)
                + floatObject("cycle", 3)
                + floatObject("bag", 4)
                + floatObject("calc", 5)

                + "    </div>\n"

                + "  </div>\n"

                + "</section>\n";
    }

    /* ==========================================================
     * FLOATING HERO OBJECT
     * ========================================================== */

    private String floatObject(
            String kind,
            int index) {

        return

                "      <figure class=\"float-item fi-"
                + kind
                + "\" style=\"--i:"
                + index
                + "\">"

                + Icons.svg(kind)

                + "</figure>\n";
    }

    /* ==========================================================
     * SEARCH PANEL
     * ========================================================== */

    private String searchPanel(
            String q,
            String category,
            String condition,
            String maxPrice,
            String sort) {

        StringBuilder sb =
                new StringBuilder();

        sb.append(
                "<section class=\"search-wrap\">\n"
        )

        .append(
                "<form class=\"search glass\" " +
                "action=\"marketplace\" " +
                "method=\"GET\" " +
                "role=\"search\">\n"
        )

        .append(
                "<label class=\"sr-only\" " +
                "for=\"q\">" +
                "Search listings" +
                "</label>\n"
        )

        .append(
                "<input class=\"search-input\" " +
                "id=\"q\" " +
                "name=\"q\" " +
                "type=\"search\" " +
                "placeholder=\"" +
                "Try: laptop under 20000 for coding...\" " +
                "value=\""
        )

        .append(
                Page.esc(q)
        )

        .append(
                "\">\n"
        )

        .append(
                "<button class=\"btn btn-primary\" " +
                "type=\"submit\">" +
                "Search" +
                "</button>\n"
        )

        .append(
                "<div class=\"filters\">\n"
        )

        .append(
                select(
                        "category",
                        "Category",
                        CATEGORIES,
                        category
                )
        )

        .append(
                select(
                        "condition",
                        "Condition",
                        CONDITIONS,
                        condition
                )
        )

        .append(
                "<label class=\"field\">" +
                "<span>Max price</span>"
        )

        .append(
                "<input name=\"maxPrice\" " +
                "type=\"number\" " +
                "min=\"1\" " +
                "step=\"1\" " +
                "placeholder=\"Any\" " +
                "value=\""
        )

        .append(
                Page.esc(maxPrice)
        )

        .append(
                "\"></label>\n"
        )

        .append(
                "<label class=\"field\">" +
                "<span>Sort</span>" +
                "<select name=\"sort\">"
        )

        .append(
                option(
                        "",
                        "Newest first",
                        sort
                )
        )

        .append(
                option(
                        "price-low",
                        "Price: low to high",
                        sort
                )
        )

        .append(
                option(
                        "price-high",
                        "Price: high to low",
                        sort
                )
        )

        .append(
                option(
                        "title",
                        "Title A to Z",
                        sort
                )
        )

        .append(
                "</select></label>\n"
        )

        .append(
                "<a class=\"clear-link\" " +
                "href=\"marketplace\">" +
                "Clear" +
                "</a>\n"
        )

        .append(
                "</div>\n"
        )

        .append(
                "</form>\n"
        )

        .append(
                "</section>\n"
        );

        return sb.toString();
    }

    /* ==========================================================
     * SELECT
     * ========================================================== */

    private String select(
            String name,
            String label,
            String[] values,
            String current) {

        StringBuilder sb =
                new StringBuilder();

        sb.append(
                "<label class=\"field\">" +
                "<span>"
        )

        .append(label)

        .append(
                "</span>" +
                "<select name=\""
        )

        .append(name)

        .append(
                "\">"
        )

        .append(
                option(
                        "",
                        "All",
                        current
                )
        );

        for (
                String value :
                values
        ) {

            sb.append(
                    option(
                            value,
                            value,
                            current
                    )
            );
        }

        sb.append(
                "</select></label>\n"
        );

        return sb.toString();
    }

    /* ==========================================================
     * OPTION
     * ========================================================== */

    private String option(
            String value,
            String label,
            String current) {

        return

                "<option value=\""
                + Page.esc(value)
                + "\""

                + (
                        value.equals(current)
                                ? " selected"
                                : ""
                )

                + ">"

                + Page.esc(label)

                + "</option>";
    }

    /* ==========================================================
     * CATEGORY ROW
     * ========================================================== */

    private String categoryRow(
            String current)
            throws IOException {

        StringBuilder sb =
                new StringBuilder(

                        "<section class=\"cats\">\n"

                        + "<h2 class=\"sr-only\">" +
                        "Categories" +
                        "</h2>\n"

                        + "<div class=\"cat-row\">\n"
                );

        for (
                String c :
                CATEGORIES
        ) {

            sb.append(
                    "<a class=\"cat-card"
            )

            .append(
                    c.equals(current)
                            ? " is-active"
                            : ""
            )

            .append(
                    "\" href=\"marketplace?category="
            )

            .append(
                    URLEncoder.encode(
                            c,
                            "UTF-8"
                    )
            )

            .append(
                    "\">"
            )

            .append(
                    "<span class=\"cat-icon\">"
            )

            .append(
                    Icons.forCategory(c)
            )

            .append(
                    "</span>"
            )

            .append(
                    "<span class=\"cat-name\">"
            )

            .append(
                    Page.esc(c)
            )

            .append(
                    "</span>"
            )

            .append(
                    "</a>\n"
            );
        }

        sb.append(
                "</div>\n"
        )

        .append(
                "</section>\n"
        );

        return sb.toString();
    }

    /* ==========================================================
     * PRODUCT CARD
     * ========================================================== */

    private String card(
            ResultSet rs)
            throws SQLException {

        int id =
                rs.getInt("listing_id");

        String title =
                rs.getString("title");

        String category =
                rs.getString("category");

        String condition =
                rs.getString("condition");

        BigDecimal price =
                rs.getBigDecimal("price");

        int quantity =
                rs.getInt("quantity");

        String seller =
                rs.getString("seller_name");

        String imageFile =
                rs.getString("image_file");

        /* ------------------------------------------------------
         * PRODUCT VISUAL
         * ------------------------------------------------------ */

        String visual;

        if (
                imageFile != null &&
                !imageFile.trim().isEmpty()
        ) {

            /*
             * The stored filename is generated by the server,
             * so it does not contain arbitrary user-controlled
             * path information.
             */
            visual =

                    "<img " +
                    "class=\"card-product-image\" " +

                    "src=\"product-image?file=" +

                    Page.esc(imageFile) +

                    "\" " +

                    "alt=\"" +

                    Page.esc(title) +

                    "\" " +

                    "loading=\"lazy\" " +

                    "decoding=\"async\">" ;

        } else {

            /*
             * If the seller did not upload an image,
             * display the existing category illustration.
             */
            visual =
                    Icons.forCategory(
                            category
                    );
        }

        /* ------------------------------------------------------
         * CONDITION CSS CLASS
         * ------------------------------------------------------ */

        String conditionClass =
                condition == null
                        ? ""
                        :
                        condition
                                .toLowerCase()
                                .replace(
                                        ' ',
                                        '-'
                                );

        /* ------------------------------------------------------
         * CARD HTML
         * ------------------------------------------------------ */

        return

                "<article " +
                "class=\"card reveal product-card\">\n"

                /* IMAGE */

                + "  <a class=\"card-visual\" " +
                "href=\"view-item?id=" +
                id +
                "\" " +
                "aria-label=\"View " +
                Page.esc(title) +
                "\">"

                + visual

                + "</a>\n"

                /* BODY */

                + "  <div class=\"card-body\">\n"

                /* CATEGORY + CONDITION */

                + "    <p class=\"card-meta\">"

                + "<span class=\"tag\">"
                + Page.esc(category)
                + "</span>"

                + "<span class=\"cond cond-"
                + conditionClass
                + "\">"
                + Page.esc(condition)
                + "</span>"

                + "</p>\n"

                /* TITLE */

                + "    <h3 class=\"card-title\">"

                + "<a href=\"view-item?id="
                + id
                + "\">"

                + Page.esc(title)

                + "</a>"

                + "</h3>\n"

                /* SELLER */

                + "    <p class=\"card-seller\">"

                + "<span class=\"avatar\">"
                + Page.esc(
                        Page.initials(
                                seller
                        )
                )
                + "</span>"

                + Page.esc(seller)

                + "</p>\n"

                /* PRICE */

                + "    <p class=\"card-price\">"
                + "&#8377;"
                + Page.money(price)

                + " <span class=\"stock\">"
                + quantity
                + " left"
                + "</span>"

                + "</p>\n"

                /* ACTIONS */

                + "    <div class=\"card-actions\">\n"

                + "      <a " +
                "class=\"btn btn-ghost btn-sm\" " +
                "href=\"view-item?id="
                + id
                + "\">"
                + "View item"
                + "</a>\n"

                + "      <form " +
                "class=\"add-form\" " +
                "action=\"add-to-cart\" " +
                "method=\"POST\">\n"

                + "        <input " +
                "type=\"hidden\" " +
                "name=\"listingId\" " +
                "value=\""
                + id
                + "\">\n"

                + "        <input " +
                "type=\"hidden\" " +
                "name=\"quantity\" " +
                "value=\"1\">\n"

                + "        <button " +
                "class=\"btn btn-primary btn-sm add-btn\" " +
                "type=\"submit\">"
                + "Add to cart"
                + "</button>\n"

                + "      </form>\n"

                + "    </div>\n"

                + "  </div>\n"

                + "</article>\n";
    }

    /* ==========================================================
     * AI SCORED CARD
     * ========================================================== */

    private static class ScoredCard {

        final double score;

        final String html;

        ScoredCard(
                double score,
                String html) {

            this.score = score;
            this.html = html;
        }
    }

    /* ==========================================================
     * SCALAR DATABASE QUERY
     * ========================================================== */

    private int scalar(
            Connection con,
            String sql)
            throws SQLException {

        try (
                PreparedStatement ps =
                        con.prepareStatement(sql);

                ResultSet rs =
                        ps.executeQuery()
        ) {

            if (rs.next()) {
                return rs.getInt(1);
            }

            return 0;
        }
    }

    /* ==========================================================
     * REQUEST PARAMETER
     * ========================================================== */

    private String param(
            HttpServletRequest request,
            String key) {

        String value =
                request.getParameter(key);

        if (value == null) {
            return "";
        }

        return value.trim();
    }
}