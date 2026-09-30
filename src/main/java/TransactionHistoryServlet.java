import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.*;
import java.text.SimpleDateFormat;

@WebServlet("/transactions")
public class TransactionHistoryServlet extends HttpServlet {

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
                            "Transactions",
                            "Log in to see your transactions",
                            "Your purchase history is tied to your account.",
                            "login.html",
                            "Log in",
                            "warn"
                    )
            );

            return;
        }

        try (
                Connection con =
                        DBConnection.getConnection()
        ) {

            BigDecimal wallet =
                    BigDecimal.ZERO;

            try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    "SELECT wallet_balance "
                                    + "FROM students "
                                    + "WHERE student_id = ?"
                            )
            ) {

                ps.setInt(
                        1,
                        me
                );

                try (
                        ResultSet rs =
                                ps.executeQuery()
                ) {

                    if (rs.next()) {

                        wallet =
                                rs.getBigDecimal(
                                        "wallet_balance"
                                );
                    }
                }
            }

            request.getSession()
                    .setAttribute(
                            "walletBalance",
                            wallet
                    );

            out.print(
                    Page.header(
                            request,
                            "Transactions",
                            "transactions"
                    )
            );

            out.print(
                    "<section class=\"txn-page\">"
            );

            out.print(
                    "<h1>Transactions</h1>"
            );

            out.print(
                    "<div class=\"wallet-card glass\">"
                    + "<span class=\"wallet-icon\">"
                    + Icons.svg("wallet")
                    + "</span>"
                    + "<div>"
                    + "<p class=\"wallet-name\">"
                    + "Campus Wallet"
                    + "</p>"
                    + "<p class=\"wallet-big\">"
                    + "&#8377;"
                    + Page.money(wallet)
                    + "</p>"
                    + "<p class=\"muted\">"
                    + "Demo marketplace balance."
                    + "</p>"
                    + "</div>"
                    + "</div>"
            );

            out.print(
                    table(
                            con,
                            me,
                            true
                    )
            );

            out.print(
                    table(
                            con,
                            me,
                            false
                    )
            );

            out.print(
                    "</section>"
            );

            out.print(
                    Page.footer()
            );

        } catch (SQLException e) {

            System.err.println(
                    "[TransactionHistoryServlet] "
                            + e.getMessage()
            );

            out.print(
                    Page.message(
                            request,
                            "Transactions",
                            "We cannot load your transactions",
                            "Check MySQL in XAMPP and reload.",
                            "marketplace",
                            "Back to marketplace",
                            "error"
                    )
            );
        }
    }

    private String table(
            Connection con,
            int me,
            boolean purchases)
            throws SQLException {

        String sql;

        if (purchases) {

            sql =
                    "SELECT t.txn_id, "
                  + "t.order_ref, "
                  + "t.amount, "
                  + "t.payment_method, "
                  + "t.payment_status, "
                  + "t.txn_date, "
                  + "t.quantity, "
                  + "l.title, "
                  + "l.listing_id, "
                  + "p.name AS other_name "
                  + "FROM transactions t "
                  + "JOIN listings l "
                  + "ON l.listing_id = t.listing_id "
                  + "JOIN students p "
                  + "ON p.student_id = t.seller_id "
                  + "WHERE t.buyer_id = ? "
                  + "ORDER BY t.txn_date DESC, "
                  + "t.txn_id DESC";

        } else {

            sql =
                    "SELECT t.txn_id, "
                  + "t.order_ref, "
                  + "t.amount, "
                  + "t.payment_method, "
                  + "t.payment_status, "
                  + "t.txn_date, "
                  + "t.quantity, "
                  + "l.title, "
                  + "l.listing_id, "
                  + "p.name AS other_name "
                  + "FROM transactions t "
                  + "JOIN listings l "
                  + "ON l.listing_id = t.listing_id "
                  + "JOIN students p "
                  + "ON p.student_id = t.buyer_id "
                  + "WHERE t.seller_id = ? "
                  + "ORDER BY t.txn_date DESC, "
                  + "t.txn_id DESC";
        }

        SimpleDateFormat date =
                new SimpleDateFormat(
                        "dd MMM yyyy, HH:mm"
                );

        StringBuilder rows =
                new StringBuilder();

        BigDecimal sum =
                BigDecimal.ZERO;

        int count = 0;

        try (
                PreparedStatement ps =
                        con.prepareStatement(sql)
        ) {

            ps.setInt(
                    1,
                    me
            );

            try (
                    ResultSet rs =
                            ps.executeQuery()
            ) {

                while (rs.next()) {

                    count++;

                    BigDecimal amount =
                            rs.getBigDecimal(
                                    "amount"
                            );

                    sum =
                            sum.add(amount);

                    Timestamp timestamp =
                            rs.getTimestamp(
                                    "txn_date"
                            );

                    String method =
                            rs.getString(
                                    "payment_method"
                            );

                    String status =
                            rs.getString(
                                    "payment_status"
                            );

                    int txnId =
                            rs.getInt("txn_id");

                    /* =========================================
                     * TRANSACTION ROW
                     * ========================================= */

                    rows.append(
                            "<tr>"
                    );

                    /* Transaction ID */

                    rows.append(
                            "<td>#"
                    )
                    .append(
                            txnId
                    )
                    .append(
                            "</td>"
                    );

                    /* Product */

                    rows.append(
                            "<td>"
                            + "<a href=\"view-item?id="
                    )
                    .append(
                            rs.getInt(
                                    "listing_id"
                            )
                    )
                    .append(
                            "\">"
                    )
                    .append(
                            Page.esc(
                                    rs.getString(
                                            "title"
                                    )
                            )
                    )
                    .append(
                            "</a>"
                            + "</td>"
                    );

                    /* Seller / Buyer */

                    rows.append(
                            "<td>"
                    )
                    .append(
                            Page.esc(
                                    rs.getString(
                                            "other_name"
                                    )
                            )
                    )
                    .append(
                            "</td>"
                    );

                    /* Quantity */

                    rows.append(
                            "<td>"
                    )
                    .append(
                            rs.getInt(
                                    "quantity"
                            )
                    )
                    .append(
                            "</td>"
                    );

                    /* Payment method */

                    rows.append(
                            "<td>"
                    )
                    .append(
                            formatPaymentMethod(
                                    method
                            )
                    )
                    .append(
                            "</td>"
                    );

                    /* Payment status */

                    rows.append(
                            "<td>"
                            + "<span class=\"payment-status\">"
                    )
                    .append(
                            Page.esc(
                                    status
                            )
                    )
                    .append(
                            "</span>"
                            + "</td>"
                    );

                    /* Amount */

                    rows.append(
                            "<td class=\"num\">"
                            + "&#8377;"
                    )
                    .append(
                            Page.money(amount)
                    )
                    .append(
                            "</td>"
                    );

                    /* Date */

                    rows.append(
                            "<td>"
                    )
                    .append(
                            timestamp == null
                                    ? ""
                                    : date.format(
                                            timestamp
                                    )
                    )
                    .append(
                            "</td>"
                    );

                    /* =========================================
                     * ACTION
                     * ========================================= */

                    if (purchases) {

                        rows.append(
                                "<td>"
                        );

                        rows.append(
                                "<a "
                                + "class=\"btn btn-primary btn-sm\" "
                                + "href=\"handover?txnId="
                        )
                        .append(
                                txnId
                        )
                        .append(
                                "\">"
                                + "Handover"
                                + "</a>"
                        );

                        rows.append(
                                "</td>"
                        );

                    } else {

                        rows.append(
                                "<td class=\"muted\">"
                                + "—"
                                + "</td>"
                        );
                    }

                    rows.append(
                            "</tr>"
                    );
                }
            }
        }

        String title =
                purchases
                        ? "Items you bought"
                        : "Items you sold";

        String empty =
                purchases
                        ? "No purchases yet."
                        : "Nothing sold yet.";

        StringBuilder sb =
                new StringBuilder();

        sb.append(
                "<div class=\"txn-block glass\">"
        );

        sb.append(
                "<h2>"
        )
        .append(
                title
        )
        .append(
                "</h2>"
        );

        /* ======================================================
         * EMPTY
         * ====================================================== */

        if (count == 0) {

            sb.append(
                    "<p class=\"muted\">"
            )
            .append(
                    empty
            )
            .append(
                    "</p>"
            );

            sb.append(
                    "<a class=\"btn btn-ghost btn-sm\" "
                    + "href=\""
            );

            sb.append(
                    purchases
                            ? "marketplace"
                            : "create-listing.html"
            );

            sb.append(
                    "\">"
            );

            sb.append(
                    purchases
                            ? "Browse marketplace"
                            : "Sell an item"
            );

            sb.append(
                    "</a>"
            );

        } else {

            /* ==================================================
             * TABLE
             * ================================================== */

            sb.append(
                    "<div class=\"table-scroll\">"
                    + "<table class=\"txn-table\">"
                    + "<thead>"
                    + "<tr>"
                    + "<th>Txn</th>"
                    + "<th>Item</th>"
                    + "<th>"
            );

            sb.append(
                    purchases
                            ? "Seller"
                            : "Buyer"
            );

            sb.append(
                    "</th>"
                    + "<th>Qty</th>"
                    + "<th>Payment</th>"
                    + "<th>Status</th>"
                    + "<th class=\"num\">Amount</th>"
                    + "<th>Date</th>"
                    + "<th>Action</th>"
                    + "</tr>"
                    + "</thead>"
                    + "<tbody>"
            );

            sb.append(
                    rows
            );

            sb.append(
                    "</tbody>"
                    + "</table>"
                    + "</div>"
            );

            /* ==================================================
             * SUMMARY
             * ================================================== */

            sb.append(
                    "<p class=\"txn-total\">"
            )
            .append(
                    count
            )
            .append(
                    purchases
                            ? " purchases"
                            : " sales"
            )
            .append(
                    " &middot; &#8377;"
            )
            .append(
                    Page.money(sum)
            )
            .append(
                    purchases
                            ? " spent"
                            : " earned"
            )
            .append(
                    "</p>"
            );
        }

        sb.append(
                "</div>"
        );

        return sb.toString();
    }

    /* ==========================================================
     * PAYMENT METHOD
     * ========================================================== */

    private String formatPaymentMethod(
            String method) {

        if (method == null) {
            return "Unknown";
        }

        if ("CAMPUS_WALLET".equalsIgnoreCase(
                method
        )) {

            return "Campus Wallet";
        }

        if ("UPI".equalsIgnoreCase(
                method
        )) {

            return "UPI";
        }

        if ("CARD".equalsIgnoreCase(
                method
        )) {

            return "Card";
        }

        return Page.esc(
                method
        );
    }
}