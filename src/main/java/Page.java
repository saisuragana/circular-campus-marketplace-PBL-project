import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.text.DecimalFormat;

/**
 * Shared HTML shell for all servlet-rendered pages.
 *
 * Responsibilities:
 * - Common HTML <head>
 * - Navigation bar
 * - Login/session state
 * - Wallet display
 * - Cart count
 * - Footer
 * - Common text formatting / escaping
 *
 * No database operations are performed here.
 */
public class Page {

    /* ----------------------------------------------------------
     * MONEY
     * ---------------------------------------------------------- */

    private static DecimalFormat money() {
        /*
         * DecimalFormat is not thread safe.
         * A new instance is therefore created for every request.
         */
        return new DecimalFormat("#,##0.00");
    }

    public static String money(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }

        return money().format(amount);
    }

    /* ----------------------------------------------------------
     * TEXT UTILITIES
     * ---------------------------------------------------------- */

    /**
     * Escapes text before inserting it into HTML.
     * Helps prevent HTML injection / basic XSS.
     */
    public static String esc(String raw) {

        if (raw == null) {
            return "";
        }

        StringBuilder sb =
                new StringBuilder(raw.length() + 16);

        for (int i = 0; i < raw.length(); i++) {

            char c = raw.charAt(i);

            switch (c) {

                case '&':
                    sb.append("&amp;");
                    break;

                case '<':
                    sb.append("&lt;");
                    break;

                case '>':
                    sb.append("&gt;");
                    break;

                case '"':
                    sb.append("&quot;");
                    break;

                case '\'':
                    sb.append("&#39;");
                    break;

                default:
                    sb.append(c);
            }
        }

        return sb.toString();
    }

    /**
     * Generates initials for the navbar/user avatar.
     */
    public static String initials(String name) {

        if (name == null || name.trim().isEmpty()) {
            return "?";
        }

        String[] parts =
                name.trim().split("\\s+");

        String result =
                parts[0].substring(0, 1);

        if (parts.length > 1) {

            result +=
                    parts[parts.length - 1]
                            .substring(0, 1);
        }

        return result.toUpperCase();
    }

    /* ----------------------------------------------------------
     * PAGE HEADER
     * ---------------------------------------------------------- */

    /**
     * Generates the common page header and navigation.
     *
     * active values:
     * marketplace
     * sell
     * listings
     * transactions
     * cart
     * ""
     */
    public static String header(
            HttpServletRequest request,
            String title,
            String active) {

        HttpSession session =
                request.getSession(false);

        boolean loggedIn =
                session != null
                && session.getAttribute("studentId") != null;

        String studentName =
                loggedIn
                        ? (String) session.getAttribute("studentName")
                        : null;

        BigDecimal wallet =
                loggedIn
                        ? (BigDecimal) session.getAttribute("walletBalance")
                        : null;

        int cartCount =
                CartUtil.countItems(request);

        StringBuilder h =
                new StringBuilder(8192);

        /* ------------------------------------------------------
         * DOCUMENT HEAD
         * ------------------------------------------------------ */

        h.append("<!DOCTYPE html>\n")
         .append("<html lang=\"en\">\n")
         .append("<head>\n")

         .append("<meta charset=\"UTF-8\">\n")

         .append(
                 "<meta name=\"viewport\" " +
                 "content=\"width=device-width, initial-scale=1\">\n"
         )

         .append(
                 "<meta name=\"theme-color\" " +
                 "content=\"#0b0f19\">\n"
         )

         .append("<title>")
         .append(esc(title))
         .append(" | Circular Campus</title>\n")

         .append(
                 "<link rel=\"stylesheet\" href=\"style.css\">\n"
         )

         .append("</head>\n")
         .append("<body>\n");

        /* ------------------------------------------------------
         * ACCESSIBILITY
         * ------------------------------------------------------ */

        h.append(
                "<a class=\"skip-link\" href=\"#main\">" +
                "Skip to content</a>\n"
        );

        /* ------------------------------------------------------
         * BACKGROUND
         * ------------------------------------------------------ */

        h.append(
                "<div class=\"aurora\" aria-hidden=\"true\"></div>\n"
        );

        /* ------------------------------------------------------
         * NAVBAR
         * ------------------------------------------------------ */

        h.append(
                "<header class=\"nav glass\">\n" +
                "  <div class=\"nav-inner\">\n"
        );

        /* ------------------------------------------------------
         * BRAND
         * ------------------------------------------------------ */

        h.append(
                "<a class=\"brand\" href=\"marketplace\" " +
                "aria-label=\"Circular Campus home\">"
        )

        .append(
                "<span class=\"brand-mark\" " +
                "aria-hidden=\"true\"></span>"
        )

        .append(
                "<span class=\"brand-text\">" +
                "Circular<em>Campus</em>" +
                "</span>"
        )

        .append("</a>\n");

        /* ------------------------------------------------------
         * MOBILE MENU BUTTON
         * ------------------------------------------------------ */

        h.append(
                "<button class=\"nav-toggle\" " +
                "id=\"navToggle\" " +
                "type=\"button\" " +
                "aria-expanded=\"false\" " +
                "aria-controls=\"navLinks\" " +
                "aria-label=\"Open menu\">"
        )

        .append("<span></span>")
        .append("<span></span>")
        .append("<span></span>")

        .append("</button>\n");

        /* ------------------------------------------------------
         * NAV LINKS
         *
         * IMPORTANT:
         * There is only ONE navigation block here.
         * ------------------------------------------------------ */

        h.append(
                "<nav class=\"nav-links\" " +
                "id=\"navLinks\" " +
                "aria-label=\"Main navigation\">\n"
        );

        /* Marketplace */

        h.append(
                navLink(
                        "marketplace",
                        "Marketplace",
                        active,
                        "marketplace"
                )
        );

        /* Sell an item */

        h.append(
                navLink(
                        "create-listing.html",
                        "Sell an item",
                        active,
                        "sell"
                )
        );

        /* Logged-in links */

        if (loggedIn) {

            h.append(
                    navLink(
                            "my-listings",
                            "My listings",
                            active,
                            "listings"
                    )
            );

            h.append(
                    navLink(
                            "transactions",
                            "Transactions",
                            active,
                            "transactions"
                    )
            );
        }

        /* ------------------------------------------------------
         * CART
         * ------------------------------------------------------ */

        h.append(
                "<a class=\"nav-link cart-link"
        )

        .append(
                "cart".equals(active)
                        ? " is-active"
                        : ""
        )

        .append(
                "\" href=\"cart\" " +
                "aria-label=\"Shopping cart\">"
        )

        .append("Cart")

        .append(
                "<span class=\"cart-badge\" " +
                "id=\"cartBadge\""
        );

        if (cartCount == 0) {
            h.append(" hidden");
        }

        h.append(">")

         .append(cartCount)

         .append("</span>")

         .append("</a>\n");

        /* ------------------------------------------------------
         * LOGGED-IN USER AREA
         * ------------------------------------------------------ */

        if (loggedIn) {

            /* --------------------------------------------------
             * WALLET
             * -------------------------------------------------- */

            h.append(
                    "<div class=\"wallet-chip\" " +
                    "id=\"walletChip\" " +
                    "tabindex=\"0\" " +
                    "role=\"button\" " +
                    "aria-expanded=\"false\" " +
                    "aria-haspopup=\"true\">"
            )

            .append(
                    "<span class=\"wallet-label\">" +
                    "Wallet</span>"
            )

            .append(
                    "<span class=\"wallet-amount\">" +
                    "&#8377;" +
                    money(wallet) +
                    "</span>"
            )

            .append(
                    "<div class=\"wallet-pop\" " +
                    "id=\"walletPop\" " +
                    "role=\"dialog\" " +
                    "aria-label=\"Campus wallet\">"
            )

            .append(
                    "<strong>Campus Wallet</strong>"
            )

            .append(
                    "<p>" +
                    "Demo balance used only inside this project. "
            )

            .append(
                    "<a href=\"add-money\">" +
                    "Add money</a>"
            )

            .append(
                    " &middot; "
            )

            .append(
                    "<a href=\"transactions\">" +
                    "See transactions</a>"
            )

            .append("</p>")
            .append("</div>")
            .append("</div>\n");

            /* --------------------------------------------------
             * USER NAME
             * -------------------------------------------------- */

            h.append(
                    "<span class=\"nav-user\" " +
                    "title=\""
            )

            .append(
                    esc(studentName)
            )

            .append("\">")

            .append(
                    esc(studentName)
            )

            .append("</span>\n");

            /* --------------------------------------------------
             * LOGOUT
             * -------------------------------------------------- */

            h.append(
                    "<form class=\"nav-logout\" " +
                    "action=\"logout\" " +
                    "method=\"POST\">"
            )

            .append(
                    "<button class=\"btn btn-ghost btn-sm\" " +
                    "type=\"submit\">" +
                    "Log out" +
                    "</button>"
            )

            .append("</form>\n");

        } else {

            /* --------------------------------------------------
             * GUEST AREA
             * -------------------------------------------------- */

            h.append(
                    "<a class=\"nav-link\" " +
                    "href=\"login.html\">" +
                    "Log in</a>\n"
            )

            .append(
                    "<a class=\"btn btn-primary btn-sm\" " +
                    "href=\"create-account.html\">" +
                    "Create account</a>\n"
            );
        }

        /* ------------------------------------------------------
         * CLOSE NAVBAR
         * ------------------------------------------------------ */

        h.append(
                "</nav>\n" +
                "  </div>\n" +
                "</header>\n"
        );

        /* ------------------------------------------------------
         * MAIN CONTENT
         * ------------------------------------------------------ */

        h.append(
                "<main id=\"main\" class=\"page\">\n"
        );

        return h.toString();
    }

    /* ----------------------------------------------------------
     * NAV LINK
     * ---------------------------------------------------------- */

    private static String navLink(
            String href,
            String label,
            String active,
            String key) {

        return
                "<a class=\"nav-link"
                + (key.equals(active)
                        ? " is-active"
                        : "")
                + "\" href=\""
                + href
                + "\">"
                + label
                + "</a>\n";
    }

    /* ----------------------------------------------------------
     * FOOTER
     * ---------------------------------------------------------- */

    public static String footer() {

        return
                "</main>\n"

                + "<footer class=\"site-foot\">\n"

                + "  <p class=\"foot-brand\">" +
                "Circular Campus</p>\n"

                + "  <p class=\"foot-tag\">" +
                "Buy less. Reuse more. Campus smarter." +
                "</p>\n"

                + "  <p class=\"foot-note\">" +
                "Web Technologies PBL &middot; " +
                "Java Servlets, JDBC and MySQL. " +
                "The wallet is a demo balance stored in the database, " +
                "not real money." +
                "</p>\n"

                + "</footer>\n"

                + "<div class=\"toast\" " +
                "id=\"toast\" " +
                "role=\"status\" " +
                "aria-live=\"polite\"></div>\n"

                + "<script src=\"script.js\"></script>\n"

                + "</body>\n"
                + "</html>";
    }

    /* ----------------------------------------------------------
     * MESSAGE PAGE
     * ---------------------------------------------------------- */

    /**
     * Full message/error page.
     *
     * tone:
     * error
     * warn
     * ok
     */
    public static String message(
            HttpServletRequest request,
            String title,
            String heading,
            String body,
            String linkHref,
            String linkText,
            String tone) {

        return
                header(
                        request,
                        title,
                        ""
                )

                + "<section class=\"notice-wrap\">\n"

                + "<div class=\"notice glass tone-"
                + esc(tone)
                + "\">\n"

                + "<h1>"
                + esc(heading)
                + "</h1>\n"

                + "<p>"
                + esc(body)
                + "</p>\n"

                + "<a class=\"btn btn-primary\" href=\""
                + esc(linkHref)
                + "\">"
                + esc(linkText)
                + "</a>\n"

                + "</div>\n"
                + "</section>\n"

                + footer();
    }
}