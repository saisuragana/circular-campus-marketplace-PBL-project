import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * POST /checkout
 *
 * Demo marketplace checkout supporting:
 *
 * CAMPUS_WALLET
 * UPI
 * CARD
 *
 * Payment is simulated for the PBL prototype.
 *
 * IMPORTANT:
 * Payment success does NOT immediately credit the seller.
 *
 * Money follows this flow:
 *
 *     Buyer payment
 *          ↓
 *     Transaction created
 *          ↓
 *     Seller amount HELD
 *          ↓
 *     Product handover
 *          ↓
 *     Handover OTP
 *          ↓
 *     90% seller release
 *          ↓
 *     10% protection hold
 *          ↓
 *     Final release
 *
 * No real payment gateway is used.
 * No sensitive card information is stored.
 */
@WebServlet("/checkout")
public class CheckoutServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /*
     * 90% is released after successful handover.
     * 10% remains temporarily protected.
     */
    private static final BigDecimal SELLER_RELEASE_PERCENT =
            new BigDecimal("0.90");

    private static final BigDecimal PROTECTION_PERCENT =
            new BigDecimal("0.10");

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        response.sendRedirect("cart");
    }

    /* ==========================================================
     * PURCHASE LINE
     * ========================================================== */

    private static class Line {

        int cartId;
        int listingId;
        int sellerId;
        int qty;

        String title;
        String sellerName;

        BigDecimal price;

        int txnId;

        BigDecimal lineTotal() {

            return price.multiply(
                    BigDecimal.valueOf(qty)
            );
        }
    }

    /* ==========================================================
     * CHECKOUT
     * ========================================================== */

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        response.setContentType(
                "text/html;charset=UTF-8"
        );

        request.setCharacterEncoding("UTF-8");

        PrintWriter out =
                response.getWriter();

        HttpSession session =
                request.getSession(false);

        Integer buyerId =
                session == null
                        ? null
                        : (Integer) session.getAttribute("studentId");

        /* ------------------------------------------------------
         * LOGIN CHECK
         * ------------------------------------------------------ */

        if (buyerId == null) {

            out.print(
                    Page.message(
                            request,
                            "Log in to check out",
                            "You need an account to pay",
                            "Log in and your cart will be waiting for you.",
                            "login.html",
                            "Log in",
                            "warn"
                    )
            );

            return;
        }

        /* ------------------------------------------------------
         * PAYMENT METHOD
         * ------------------------------------------------------ */

        String paymentMethod =
                request.getParameter("paymentMethod");

        if (paymentMethod == null) {
            paymentMethod = "";
        }

        paymentMethod =
                paymentMethod.trim().toUpperCase();

        if (!paymentMethod.equals("CAMPUS_WALLET")
                && !paymentMethod.equals("UPI")
                && !paymentMethod.equals("CARD")) {

            out.print(
                    fail(
                            request,
                            "Choose a payment method",
                            "Select Campus Wallet, UPI or Card."
                    )
            );

            return;
        }

        /* ------------------------------------------------------
         * PAYMENT VALIDATION
         * ------------------------------------------------------ */

        String upiId =
                request.getParameter("upiId");

        String cardNumber =
                request.getParameter("cardNumber");

        String cardExpiry =
                request.getParameter("cardExpiry");

        String cardCvv =
                request.getParameter("cardCvv");

        /* UPI */

        if ("UPI".equals(paymentMethod)) {

            if (upiId == null
                    || !upiId.matches(
                            "^[A-Za-z0-9._-]+@[A-Za-z0-9._-]+$")) {

                out.print(
                        fail(
                                request,
                                "Invalid UPI ID",
                                "Enter a demo UPI ID such as student@upi."
                        )
                );

                return;
            }
        }

        /* CARD */

        if ("CARD".equals(paymentMethod)) {

            String digits =
                    cardNumber == null
                            ? ""
                            : cardNumber.replaceAll("\\s+", "");

            if (!digits.matches("\\d{16}")) {

                out.print(
                        fail(
                                request,
                                "Invalid card number",
                                "Enter a 16-digit demo card number."
                        )
                );

                return;
            }

            if (cardExpiry == null
                    || !cardExpiry.matches(
                            "^(0[1-9]|1[0-2])/\\d{2}$")) {

                out.print(
                        fail(
                                request,
                                "Invalid expiry",
                                "Use MM/YY format."
                        )
                );

                return;
            }

            if (cardCvv == null
                    || !cardCvv.matches("\\d{3,4}")) {

                out.print(
                        fail(
                                request,
                                "Invalid CVV",
                                "Enter a 3 or 4 digit CVV."
                        )
                );

                return;
            }

            /*
             * IMPORTANT:
             * Card details are never stored.
             */
        }

        Connection con = null;

        try {

            con =
                    DBConnection.getConnection();

            con.setAutoCommit(false);

            /* ==================================================
             * 1. LOAD CART + LOCK PRODUCTS
             * ================================================== */

            List<Line> lines =
                    new ArrayList<Line>();

            String cartSql =
                    "SELECT c.cart_id, "
                  + "c.quantity AS want, "
                  + "l.listing_id, "
                  + "l.title, "
                  + "l.price, "
                  + "l.quantity AS stock, "
                  + "l.status, "
                  + "l.seller_id, "
                  + "s.name AS seller_name "
                  + "FROM cart_items c "
                  + "JOIN listings l "
                  + "ON l.listing_id = c.listing_id "
                  + "JOIN students s "
                  + "ON s.student_id = l.seller_id "
                  + "WHERE c.student_id = ? "
                  + "FOR UPDATE";

            try (
                    PreparedStatement ps =
                            con.prepareStatement(cartSql)
            ) {

                ps.setInt(
                        1,
                        buyerId
                );

                try (
                        ResultSet rs =
                                ps.executeQuery()
                ) {

                    while (rs.next()) {

                        Line line =
                                new Line();

                        line.cartId =
                                rs.getInt("cart_id");

                        line.qty =
                                rs.getInt("want");

                        line.listingId =
                                rs.getInt("listing_id");

                        line.title =
                                rs.getString("title");

                        line.price =
                                rs.getBigDecimal("price");

                        line.sellerId =
                                rs.getInt("seller_id");

                        line.sellerName =
                                rs.getString("seller_name");

                        int stock =
                                rs.getInt("stock");

                        String status =
                                rs.getString("status");

                        /* Quantity */

                        if (line.qty < 1) {

                            rollback(con);

                            out.print(
                                    fail(
                                            request,
                                            "Invalid quantity",
                                            "Remove the item and add it again."
                                    )
                            );

                            return;
                        }

                        /* Stock */

                        if (!"Available".equalsIgnoreCase(status)
                                || stock < line.qty) {

                            rollback(con);

                            out.print(
                                    fail(
                                            request,
                                            "\"" + line.title
                                                    + "\" is unavailable",
                                            "The available stock changed. "
                                                    + "Please update your cart."
                                    )
                            );

                            return;
                        }

                        /* Own product */

                        if (line.sellerId == buyerId) {

                            rollback(con);

                            out.print(
                                    fail(
                                            request,
                                            "You cannot buy your own listing",
                                            "Remove your own item from the cart."
                                    )
                            );

                            return;
                        }

                        lines.add(line);
                    }
                }
            }

            /* Empty cart */

            if (lines.isEmpty()) {

                rollback(con);

                out.print(
                        fail(
                                request,
                                "Your cart is empty",
                                "Add something from the marketplace first."
                        )
                );

                return;
            }

            /* ==================================================
             * 2. CALCULATE TOTAL
             * ================================================== */

            BigDecimal total =
                    BigDecimal.ZERO;

            for (Line line : lines) {

                total =
                        total.add(
                                line.lineTotal()
                        );
            }

            total =
                    total.setScale(
                            2,
                            RoundingMode.HALF_UP
                    );

            /* ==================================================
             * 3. LOCK BUYER WALLET
             * ================================================== */

            BigDecimal buyerWallet =
                    BigDecimal.ZERO;

            try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    "SELECT wallet_balance "
                                  + "FROM students "
                                  + "WHERE student_id = ? "
                                  + "FOR UPDATE"
                            )
            ) {

                ps.setInt(
                        1,
                        buyerId
                );

                try (
                        ResultSet rs =
                                ps.executeQuery()
                ) {

                    if (!rs.next()) {

                        rollback(con);

                        out.print(
                                fail(
                                        request,
                                        "Account not found",
                                        "Please log in again."
                                )
                        );

                        return;
                    }

                    buyerWallet =
                            rs.getBigDecimal(
                                    "wallet_balance"
                            );
                }
            }

            /* ==================================================
             * 4. PAYMENT
             * ================================================== */

            if ("CAMPUS_WALLET".equals(paymentMethod)) {

                if (buyerWallet.compareTo(total) < 0) {

                    rollback(con);

                    out.print(
                            fail(
                                    request,
                                    "Insufficient wallet balance",
                                    "You need "
                                            + Page.money(total)
                                            + " but your wallet contains "
                                            + Page.money(buyerWallet)
                                            + "."
                            )
                    );

                    return;
                }

                /*
                 * Buyer pays.
                 *
                 * IMPORTANT:
                 * Seller is NOT credited here.
                 *
                 * The money is considered HELD by the
                 * marketplace simulation until handover.
                 */

                try (
                        PreparedStatement ps =
                                con.prepareStatement(
                                        "UPDATE students "
                                      + "SET wallet_balance = "
                                      + "wallet_balance - ? "
                                      + "WHERE student_id = ?"
                                )
                ) {

                    ps.setBigDecimal(
                            1,
                            total
                    );

                    ps.setInt(
                            2,
                            buyerId
                    );

                    ps.executeUpdate();
                }
            }

            /*
             * UPI and CARD are simulated.
             *
             * No real external money movement occurs.
             */

            /* ==================================================
             * 5. ORDER + PAYMENT REFERENCES
             * ================================================== */

            String orderRef =
                    "CMP-"
                    + new SimpleDateFormat(
                            "yyyyMMddHHmmss"
                    ).format(
                            new java.util.Date()
                    )
                    + "-"
                    + UUID.randomUUID()
                            .toString()
                            .substring(
                                    0,
                                    4
                            )
                            .toUpperCase();

            String paymentReference =
                    "PAY-"
                    + UUID.randomUUID()
                            .toString()
                            .substring(
                                    0,
                                    8
                            )
                            .toUpperCase();

            /* ==================================================
             * 6. INSERT TRANSACTIONS
             * ================================================== */

            String insertSql =
                    "INSERT INTO transactions "
                  + "(order_ref, buyer_id, seller_id, listing_id, "
                  + "amount, payment_method, payment_status, "
                  + "payment_reference, quantity) "
                  + "VALUES (?,?,?,?,?,?,?,?,?)";

            try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    insertSql,
                                    Statement.RETURN_GENERATED_KEYS
                            )
            ) {

                for (Line line : lines) {

                    ps.setString(
                            1,
                            orderRef
                    );

                    ps.setInt(
                            2,
                            buyerId
                    );

                    ps.setInt(
                            3,
                            line.sellerId
                    );

                    ps.setInt(
                            4,
                            line.listingId
                    );

                    ps.setBigDecimal(
                            5,
                            line.lineTotal()
                    );

                    ps.setString(
                            6,
                            paymentMethod
                    );

                    /*
                     * SUCCESS refers to PAYMENT status,
                     * not product handover.
                     */
                    ps.setString(
                            7,
                            "SUCCESS"
                    );

                    ps.setString(
                            8,
                            paymentReference
                    );

                    ps.setInt(
                            9,
                            line.qty
                    );

                    ps.executeUpdate();

                    try (
                            ResultSet keys =
                                    ps.getGeneratedKeys()
                    ) {

                        if (keys.next()) {

                            line.txnId =
                                    keys.getInt(1);
                        }
                    }

                    if (line.txnId <= 0) {

                        throw new SQLException(
                                "Transaction ID was not generated."
                        );
                    }
                }
            }

            /* ==================================================
             * 7. CREATE SETTLEMENT / ESCROW RECORD
             * ================================================== */

            String settlementSql =
                    "INSERT INTO transaction_settlements "
                  + "(txn_id, gross_amount, "
                  + "seller_released_amount, "
                  + "protection_amount, "
                  + "settlement_status, "
                  + "pickup_status) "
                  + "VALUES (?, ?, 0.00, ?, 'HELD', 'PENDING')";

            try (
                    PreparedStatement ps =
                            con.prepareStatement(settlementSql)
            ) {

                for (Line line : lines) {

                    BigDecimal lineTotal =
                            line.lineTotal()
                                .setScale(
                                        2,
                                        RoundingMode.HALF_UP
                                );

                    BigDecimal protection =
                            lineTotal
                                .multiply(
                                        PROTECTION_PERCENT
                                )
                                .setScale(
                                        2,
                                        RoundingMode.HALF_UP
                                );

                    /*
                     * Protect against rounding producing
                     * a total different from the original amount.
                     */
                    BigDecimal initialSellerRelease =
                            lineTotal.subtract(
                                    protection
                            );

                    if (initialSellerRelease.compareTo(
                            BigDecimal.ZERO) < 0) {

                        initialSellerRelease =
                                BigDecimal.ZERO;
                    }

                    ps.setInt(
                            1,
                            line.txnId
                    );

                    ps.setBigDecimal(
                            2,
                            lineTotal
                    );

                    ps.setBigDecimal(
                            3,
                            protection
                    );

                    ps.executeUpdate();
                }
            }

            /* ==================================================
             * 8. UPDATE STOCK
             * ================================================== */

            try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    "UPDATE listings "
                                  + "SET status = CASE "
                                  + "WHEN quantity - ? <= 0 "
                                  + "THEN 'Sold' "
                                  + "ELSE status END, "
                                  + "quantity = quantity - ? "
                                  + "WHERE listing_id = ? "
                                  + "AND quantity >= ?"
                            )
            ) {

                for (Line line : lines) {

                    ps.setInt(
                            1,
                            line.qty
                    );

                    ps.setInt(
                            2,
                            line.qty
                    );

                    ps.setInt(
                            3,
                            line.listingId
                    );

                    ps.setInt(
                            4,
                            line.qty
                    );

                    if (ps.executeUpdate() != 1) {

                        rollback(con);

                        out.print(
                                fail(
                                        request,
                                        "Stock changed",
                                        "The item changed while you were paying. "
                                                + "Nothing was completed."
                                )
                        );

                        return;
                    }
                }
            }

            /* ==================================================
             * 9. CLEAR CART
             * ================================================== */

            try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    "DELETE FROM cart_items "
                                  + "WHERE cart_id = ? "
                                  + "AND student_id = ?"
                            )
            ) {

                for (Line line : lines) {

                    ps.setInt(
                            1,
                            line.cartId
                    );

                    ps.setInt(
                            2,
                            buyerId
                    );

                    ps.executeUpdate();
                }
            }

            /* ==================================================
             * 10. COMMIT
             * ================================================== */

            con.commit();

            /*
             * Update session wallet.
             *
             * Only Campus Wallet changes the buyer's
             * demo wallet balance.
             */

            BigDecimal remaining =
                    buyerWallet;

            if ("CAMPUS_WALLET".equals(paymentMethod)) {

                remaining =
                        buyerWallet.subtract(
                                total
                        );
            }

            session.setAttribute(
                    "walletBalance",
                    remaining
            );

            /* ==================================================
             * SUCCESS RECEIPT
             * ================================================== */

            out.print(
                    receipt(
                            request,
                            lines,
                            total,
                            remaining,
                            paymentMethod,
                            orderRef,
                            paymentReference
                    )
            );

        } catch (SQLException e) {

            System.err.println(
                    "[CheckoutServlet] "
                            + e.getMessage()
            );

            rollback(con);

            out.print(
                    fail(
                            request,
                            "Payment could not be completed",
                            "Nothing was completed. Please try again."
                    )
            );

        } finally {

            if (con != null) {

                try {

                    con.setAutoCommit(true);
                    con.close();

                } catch (SQLException ignored) {
                }
            }
        }
    }

    /* ==========================================================
     * ROLLBACK
     * ========================================================== */

    private void rollback(Connection con) {

        if (con == null) {
            return;
        }

        try {

            con.rollback();

        } catch (SQLException e) {

            System.err.println(
                    "[CheckoutServlet] rollback failed: "
                            + e.getMessage()
            );
        }
    }

    /* ==========================================================
     * FAILURE PAGE
     * ========================================================== */

    private String fail(
            HttpServletRequest request,
            String heading,
            String body) {

        return Page.message(
                request,
                "Checkout",
                heading,
                body,
                "cart",
                "Back to cart",
                "warn"
        );
    }

    /* ==========================================================
     * SUCCESS RECEIPT
     * ========================================================== */

    private String receipt(
            HttpServletRequest request,
            List<Line> lines,
            BigDecimal total,
            BigDecimal remaining,
            String paymentMethod,
            String orderRef,
            String paymentReference) {

        StringBuilder sb =
                new StringBuilder();

        String paymentLabel;

        if ("CAMPUS_WALLET".equals(paymentMethod)) {

            paymentLabel =
                    "Campus Wallet";

        } else if ("UPI".equals(paymentMethod)) {

            paymentLabel =
                    "UPI";

        } else {

            paymentLabel =
                    "Debit / Credit Card";
        }

        sb.append(
                Page.header(
                        request,
                        "Purchase complete",
                        ""
                )
        );

        sb.append(
                "<section class=\"receipt-wrap\">"
        );

        sb.append(
                "<div class=\"receipt glass\">"
        );

        sb.append(
                "<div class=\"tick\">"
        )
        .append(
                Icons.svg("check")
        )
        .append("</div>");

        sb.append(
                "<h1>Payment successful</h1>"
        );

        sb.append(
                "<p class=\"muted\">"
        )
        .append(
                "Your payment was successful. "
                + "The seller's payment is held until "
                + "the product handover is verified."
        )
        .append("</p>");

        /* ------------------------------------------------------
         * ORDER INFORMATION
         * ------------------------------------------------------ */

        sb.append(
                "<div class=\"payment-info glass\">"
        );

        sb.append(
                "<p><span>Order ID</span><strong>"
        )
        .append(
                Page.esc(orderRef)
        )
        .append("</strong></p>");

        sb.append(
                "<p><span>Payment method</span><strong>"
        )
        .append(
                Page.esc(paymentLabel)
        )
        .append("</strong></p>");

        sb.append(
                "<p><span>Payment status</span>"
        );

        sb.append(
                "<strong class=\"success-text\">SUCCESS</strong>"
        );

        sb.append("</p>");

        sb.append(
                "<p><span>Seller settlement</span>"
                + "<strong>HELD</strong></p>"
        );

        sb.append(
                "<p><span>Payment reference</span><strong>"
        )
        .append(
                Page.esc(paymentReference)
        )
        .append("</strong></p>");

        sb.append("</div>");

        /* ------------------------------------------------------
         * PURCHASED ITEMS
         * ------------------------------------------------------ */

        sb.append(
                "<ul class=\"receipt-list\">"
        );

        for (Line line : lines) {

            sb.append("<li>")
              .append("<span class=\"r-id\">#")
              .append(line.txnId)
              .append("</span>")
              .append("<span class=\"r-title\">")
              .append(Page.esc(line.title))
              .append(" &times; ")
              .append(line.qty)
              .append("</span>")
              .append("<span class=\"r-seller muted\">")
              .append(Page.esc(line.sellerName))
              .append("</span>")
              .append("<span class=\"r-amt\">&#8377;")
              .append(Page.money(line.lineTotal()))
              .append("</span>")
              .append("</li>");
        }

        sb.append("</ul>");

        /* ------------------------------------------------------
         * TOTAL
         * ------------------------------------------------------ */

        sb.append(
                "<p class=\"sum-line sum-total\">"
        )
        .append("<span>Total paid</span>")
        .append("<strong>&#8377;")
        .append(Page.money(total))
        .append("</strong></p>");

        /* ------------------------------------------------------
         * WALLET
         * ------------------------------------------------------ */

        sb.append(
                "<p class=\"sum-line\">"
        )
        .append("<span>Wallet balance</span>")
        .append("<strong>&#8377;")
        .append(Page.money(remaining))
        .append("</strong></p>");

        /* ------------------------------------------------------
         * NEXT STEP
         * ------------------------------------------------------ */

        sb.append(
                "<div class=\"glass\" "
                + "style=\"margin-top:20px;padding:18px;\">"
        );

        sb.append(
                "<strong>Next step: arrange the handover</strong>"
        );

        sb.append(
                "<p class=\"muted\" "
                + "style=\"margin:8px 0 0;\">"
                + "Contact the seller and arrange a campus "
                + "pickup. After you receive the product, "
                + "a transaction-specific handover OTP will "
                + "be used to verify the handover."
                + "</p>"
        );

        sb.append("</div>");

        /* ------------------------------------------------------
         * BUTTONS
         * ------------------------------------------------------ */

        sb.append(
                "<div class=\"receipt-actions\">"
        );

        sb.append(
                "<a class=\"btn btn-primary\" "
                + "href=\"marketplace\">"
                + "Continue shopping"
                + "</a>"
        );

        sb.append(
                "<a class=\"btn btn-ghost\" "
                + "href=\"transactions\">"
                + "View transactions"
                + "</a>"
        );

        sb.append("</div>");

        sb.append("</div>");
        sb.append("</section>");

        sb.append(
                Page.footer()
        );

        return sb.toString();
    }
}