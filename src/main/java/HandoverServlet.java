import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Random;

@WebServlet("/handover")
public class HandoverServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final BigDecimal SELLER_RELEASE_PERCENT =
            new BigDecimal("0.90");

    private static final int PROTECTION_HOURS = 24;

    private final Random random = new Random();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        response.setContentType("text/html;charset=UTF-8");

        Integer me = CartUtil.studentId(request);

        if (me == null) {
            response.sendRedirect("login.html");
            return;
        }

        int txnId;

        try {
            txnId = Integer.parseInt(
                    request.getParameter("txnId")
            );
        } catch (Exception e) {

            response.getWriter().print(
                    Page.message(
                            request,
                            "Invalid transaction",
                            "Transaction not found",
                            "The transaction ID is invalid.",
                            "transactions",
                            "Back to transactions",
                            "warn"
                    )
            );

            return;
        }

        try (Connection con = DBConnection.getConnection()) {

            /*
             * Automatically release the final protection amount
             * if the 24-hour protection period has expired.
             */
            releaseProtectionIfEligible(con, txnId);

            TransactionInfo tx =
                    loadTransaction(con, txnId, me);

            if (tx == null) {

                response.getWriter().print(
                        Page.message(
                                request,
                                "Access denied",
                                "This transaction is not available",
                                "Only the buyer and seller involved in the transaction can access it.",
                                "transactions",
                                "Back to transactions",
                                "warn"
                        )
                );

                return;
            }

            SettlementInfo settlement =
                    loadSettlement(con, txnId);

            if (settlement == null) {

                response.getWriter().print(
                        Page.message(
                                request,
                                "Settlement unavailable",
                                "Settlement information is missing",
                                "This transaction does not have a settlement record yet.",
                                "transactions",
                                "Back to transactions",
                                "warn"
                        )
                );

                return;
            }

            boolean isBuyer =
                    me.intValue() == tx.buyerId;

            boolean isSeller =
                    me.intValue() == tx.sellerId;

            PrintWriter out =
                    response.getWriter();

            out.print(
                    Page.header(
                            request,
                            "Product Handover",
                            "transactions"
                    )
            );

            out.print(
                    "<main class=\"page-shell\">"
            );

            /*
             * ---------------------------------------------------------
             * HERO
             * ---------------------------------------------------------
             */

            out.print(
                    "<section class=\"page-hero glass\">" +

                    "<span class=\"eyebrow\">CAMPUS PROTECTION</span>" +

                    "<h1>Product handover</h1>" +

                    "<p>" +
                    Page.esc(tx.title) +
                    " · Order " +
                    Page.esc(tx.orderRef) +
                    "</p>" +

                    "</section>"
            );

            /*
             * ---------------------------------------------------------
             * TRANSACTION STATUS
             * ---------------------------------------------------------
             */

            out.print(
                    "<section class=\"glass\" " +
                    "style=\"margin-top:1.5rem;padding:1.5rem;\">" +

                    "<h2>Transaction status</h2>"
            );

            infoRow(
                    out,
                    "Payment",
                    tx.paymentStatus
            );

            infoRow(
                    out,
                    "Amount",
                    Page.money(tx.amount)
            );

            infoRow(
                    out,
                    "Settlement",
                    settlement.settlementStatus
            );

            infoRow(
                    out,
                    "Pickup",
                    settlement.pickupStatus
            );

            infoRow(
                    out,
                    "Seller released",
                    Page.money(
                            settlement.sellerReleasedAmount
                    )
            );

            infoRow(
                    out,
                    "Protection hold",
                    Page.money(
                            settlement.protectionAmount
                    )
            );

            if (settlement.protectionUntil != null) {

                infoRow(
                        out,
                        "Protection until",
                        settlement.protectionUntil.toString()
                );
            }

            out.print("</section>");

            /*
             * ---------------------------------------------------------
             * BUYER PICKUP ARRANGEMENT
             * ---------------------------------------------------------
             */

            if (isBuyer
                    && "SUCCESS".equalsIgnoreCase(tx.paymentStatus)
                    && "HELD".equalsIgnoreCase(
                            settlement.settlementStatus
                    )
                    && "PENDING".equalsIgnoreCase(
                            settlement.pickupStatus
                    )) {

                renderPickupForm(
                        out,
                        txnId
                );
            }

            /*
             * ---------------------------------------------------------
             * SCHEDULED PICKUP
             * ---------------------------------------------------------
             */

            if ("SCHEDULED".equalsIgnoreCase(
                    settlement.pickupStatus
            )) {

                out.print(
                        "<section class=\"glass\" " +
                        "style=\"margin-top:1.5rem;padding:1.5rem;\">" +

                        "<h2>Pickup scheduled</h2>"
                );

                if (settlement.pickupDate != null) {

                    infoRow(
                            out,
                            "Date",
                            settlement.pickupDate.toString()
                    );
                }

                if (settlement.pickupTime != null) {

                    infoRow(
                            out,
                            "Time",
                            settlement.pickupTime
                    );
                }

                if (settlement.pickupLocation != null) {

                    infoRow(
                            out,
                            "Location",
                            settlement.pickupLocation
                    );
                }

                out.print("</section>");
            }

            /*
             * ---------------------------------------------------------
             * CAMPUS CONTACT
             * ---------------------------------------------------------
             */

            out.print(
                    "<section class=\"glass\" " +
                    "style=\"margin-top:1.5rem;padding:1.5rem;\">" +

                    "<h2>Campus contact</h2>"
            );

            if (isBuyer) {

                out.print(
                        "<p>" +
                        "<strong>Seller:</strong> " +
                        Page.esc(tx.sellerName) +
                        "</p>"
                );

                out.print(
                        "<p class=\"muted\">" +
                        "Woxsen email: " +
                        Page.esc(tx.sellerEmail) +
                        "</p>"
                );

                if (tx.sellerPhone != null
                        && !tx.sellerPhone.trim().isEmpty()) {

                    out.print(
                            "<p class=\"muted\">" +
                            "Phone: " +
                            Page.esc(tx.sellerPhone) +
                            "</p>"
                    );
                }

            } else {

                out.print(
                        "<p>" +
                        "<strong>Buyer:</strong> " +
                        Page.esc(tx.buyerName) +
                        "</p>"
                );

                out.print(
                        "<p class=\"muted\">" +
                        "Woxsen email: " +
                        Page.esc(tx.buyerEmail) +
                        "</p>"
                );

                if (tx.buyerPhone != null
                        && !tx.buyerPhone.trim().isEmpty()) {

                    out.print(
                            "<p class=\"muted\">" +
                            "Phone: " +
                            Page.esc(tx.buyerPhone) +
                            "</p>"
                    );
                }
            }

            /*
             * CHAT BUTTON
             */

            out.print(
                    "<div style=\"margin-top:1rem;\">" +

                    "<a class=\"btn btn-primary\" " +
                    "href=\"chat?txnId=" +
                    txnId +
                    "\">" +

                    "💬 Chat with " +
                    Page.esc(
                            isBuyer
                                    ? tx.sellerName
                                    : tx.buyerName
                    ) +

                    "</a>" +

                    "</div>"
            );

            out.print("</section>");

            /*
             * ---------------------------------------------------------
             * SELLER OTP GENERATION
             * ---------------------------------------------------------
             */

            if (isSeller
                    && "SUCCESS".equalsIgnoreCase(
                            tx.paymentStatus
                    )
                    && "SCHEDULED".equalsIgnoreCase(
                            settlement.pickupStatus
                    )
                    && "PICKUP_SCHEDULED".equalsIgnoreCase(
                            settlement.settlementStatus
                    )) {

                renderSellerOtpSection(
                        out,
                        txnId,
                        settlement.handoverCode
                );
            }

            /*
             * ---------------------------------------------------------
             * BUYER OTP VERIFICATION
             * ---------------------------------------------------------
             */

            if (isBuyer
                    && "SCHEDULED".equalsIgnoreCase(
                            settlement.pickupStatus
                    )
                    && "PICKUP_SCHEDULED".equalsIgnoreCase(
                            settlement.settlementStatus
                    )
                    && settlement.handoverCode != null
                    && !settlement.handoverCode.trim().isEmpty()) {

                renderOtpVerification(
                        out,
                        txnId
                );
            }

            /*
             * ---------------------------------------------------------
             * HANDOVER VERIFIED
             * ---------------------------------------------------------
             */

            if ("PROTECTION_HOLD".equalsIgnoreCase(
                    settlement.settlementStatus
            )) {

                out.print(
                        "<section class=\"glass\" " +
                        "style=\"margin-top:1.5rem;padding:1.5rem;\">" +

                        "<h2>Handover verified ✓</h2>" +

                        "<p>" +
                        "The product handover has been verified. " +
                        "90% has been released to the seller." +
                        "</p>" +

                        "<p>" +
                        "The remaining " +
                        Page.money(
                                settlement.protectionAmount
                        ) +
                        " is protected for " +
                        PROTECTION_HOURS +
                        " hours." +
                        "</p>"
                );

                if (settlement.protectionUntil != null) {

                    out.print(
                            "<p class=\"muted\">" +
                            "Protection ends: " +
                            Page.esc(
                                    settlement.protectionUntil.toString()
                            ) +
                            "</p>"
                    );
                }

                out.print(
                        "</section>"
                );
            }

            /*
             * ---------------------------------------------------------
             * COMPLETED
             * ---------------------------------------------------------
             */

            if ("COMPLETED".equalsIgnoreCase(
                    settlement.settlementStatus
            )) {

                out.print(
                        "<section class=\"glass\" " +
                        "style=\"margin-top:1.5rem;padding:1.5rem;\">" +

                        "<h2>Transaction completed ✓</h2>" +

                        "<p>" +
                        "The protection period has ended and the " +
                        "remaining amount has been released to the seller." +
                        "</p>" +

                        "<p><strong>Seller received:</strong> " +
                        Page.money(
                                settlement.sellerReleasedAmount
                        ) +
                        "</p>" +

                        "</section>"
                );
            }

            /*
             * ---------------------------------------------------------
             * REPORT PROBLEM
             * ---------------------------------------------------------
             */

            if (isBuyer
                    && "SUCCESS".equalsIgnoreCase(
                            tx.paymentStatus
                    )
                    && !"COMPLETED".equalsIgnoreCase(
                            settlement.settlementStatus
                    )) {

                out.print(
                        "<section class=\"glass\" " +
                        "style=\"margin-top:1.5rem;padding:1.5rem;\">" +

                        "<h2>Problem with the product?</h2>" +

                        "<p class=\"muted\">" +
                        "If the product is damaged, incorrect, " +
                        "missing or different from the listing, " +
                        "you can report the problem with evidence." +
                        "</p>" +

                        "<a class=\"btn btn-danger\" " +
                        "href=\"report-problem?txnId=" +
                        txnId +
                        "\">" +

                        "Report Problem" +

                        "</a>" +

                        "</section>"
                );
            }

            /*
             * ---------------------------------------------------------
             * BACK
             * ---------------------------------------------------------
             */

            out.print(
                    "<div style=\"margin-top:1.5rem;\">" +

                    "<a class=\"btn btn-ghost\" " +
                    "href=\"transactions\">" +

                    "← Back to transactions" +

                    "</a>" +

                    "</div>"
            );

            out.print("</main>");

            out.print(Page.footer());

        } catch (SQLException e) {

            e.printStackTrace();

            response.getWriter().print(
                    Page.message(
                            request,
                            "Handover error",
                            "Unable to load the handover page",
                            "Please try again.",
                            "transactions",
                            "Back to transactions",
                            "error"
                    )
            );
        }
    }

    /*
     * ================================================================
     * POST
     * ================================================================
     */

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        request.setCharacterEncoding("UTF-8");

        Integer me = CartUtil.studentId(request);

        if (me == null) {
            response.sendRedirect("login.html");
            return;
        }

        int txnId;

        try {

            txnId = Integer.parseInt(
                    request.getParameter("txnId")
            );

        } catch (Exception e) {

            response.sendRedirect("transactions");
            return;
        }

        String action =
                request.getParameter("action");

        if (action == null) {
            response.sendRedirect(
                    "handover?txnId=" + txnId
            );
            return;
        }

        action = action.trim();

        if ("arrange".equalsIgnoreCase(action)) {

            arrangePickup(
                    request,
                    response,
                    me,
                    txnId
            );

            return;
        }

        if ("generateOtp".equalsIgnoreCase(action)) {

            generateOtp(
                    request,
                    response,
                    me,
                    txnId
            );

            return;
        }

        if ("verifyOtp".equalsIgnoreCase(action)) {

            verifyOtp(
                    request,
                    response,
                    me,
                    txnId
            );

            return;
        }

        response.sendRedirect(
                "handover?txnId=" + txnId
        );
    }

    /*
     * ================================================================
     * ARRANGE PICKUP
     * ================================================================
     */

    private void arrangePickup(
            HttpServletRequest request,
            HttpServletResponse response,
            int buyerId,
            int txnId)
            throws IOException {

        String pickupDate =
                clean(request.getParameter("pickupDate"));

        String pickupTime =
                clean(request.getParameter("pickupTime"));

        String pickupLocation =
                clean(request.getParameter("pickupLocation"));

        if (pickupDate.isEmpty()
                || pickupTime.isEmpty()
                || pickupLocation.isEmpty()) {

            response.getWriter().print(
                    Page.message(
                            request,
                            "Pickup details required",
                            "Complete all pickup fields",
                            "Enter the date, time and campus location.",
                            "handover?txnId=" + txnId,
                            "Back to handover",
                            "warn"
                    )
            );

            return;
        }

        try {

            LocalDate date =
                    LocalDate.parse(pickupDate);

            if (date.isBefore(LocalDate.now())) {

                response.getWriter().print(
                        Page.message(
                                request,
                                "Invalid pickup date",
                                "Pickup date cannot be in the past",
                                "Choose today or a future date.",
                                "handover?txnId=" + txnId,
                                "Back",
                                "warn"
                        )
                );

                return;
            }

        } catch (Exception e) {

            response.getWriter().print(
                    Page.message(
                            request,
                            "Invalid pickup date",
                            "Please select a valid date",
                            "Use the date picker.",
                            "handover?txnId=" + txnId,
                            "Back",
                            "warn"
                    )
            );

            return;
        }

        try (Connection con =
                     DBConnection.getConnection()) {

            con.setAutoCommit(false);

            TransactionInfo tx =
                    loadTransaction(
                            con,
                            txnId,
                            buyerId
                    );

            if (tx == null
                    || tx.buyerId != buyerId) {

                con.rollback();

                response.sendRedirect("transactions");
                return;
            }

            String sql =
                    "UPDATE transaction_settlements ts " +
                    "JOIN transactions t " +
                    "ON t.txn_id = ts.txn_id " +
                    "SET ts.pickup_status = 'SCHEDULED', " +
                    "ts.pickup_date = ?, " +
                    "ts.pickup_time = ?, " +
                    "ts.pickup_location = ?, " +
                    "ts.settlement_status = 'PICKUP_SCHEDULED' " +
                    "WHERE ts.txn_id = ? " +
                    "AND t.buyer_id = ? " +
                    "AND t.payment_status = 'SUCCESS' " +
                    "AND ts.settlement_status = 'HELD' " +
                    "AND ts.pickup_status = 'PENDING'";

            int updated;

            try (PreparedStatement ps =
                         con.prepareStatement(sql)) {

                ps.setDate(
                        1,
                        Date.valueOf(pickupDate)
                );

                ps.setString(
                        2,
                        pickupTime
                );

                ps.setString(
                        3,
                        pickupLocation
                );

                ps.setInt(
                        4,
                        txnId
                );

                ps.setInt(
                        5,
                        buyerId
                );

                updated =
                        ps.executeUpdate();
            }

            if (updated != 1) {

                con.rollback();

                response.getWriter().print(
                        Page.message(
                                request,
                                "Pickup unavailable",
                                "Pickup could not be arranged",
                                "The transaction may already have moved to another stage.",
                                "handover?txnId=" + txnId,
                                "Back",
                                "warn"
                        )
                );

                return;
            }

            con.commit();

            response.sendRedirect(
                    "handover?txnId=" + txnId
            );

        } catch (SQLException e) {

            response.getWriter().print(
                    Page.message(
                            request,
                            "Pickup error",
                            "Could not arrange pickup",
                            "Please try again.",
                            "handover?txnId=" + txnId,
                            "Back",
                            "error"
                    )
            );
        }
    }

    /*
     * ================================================================
     * GENERATE OTP
     * ================================================================
     */

    private void generateOtp(
            HttpServletRequest request,
            HttpServletResponse response,
            int sellerId,
            int txnId)
            throws IOException {

        try (Connection con =
                     DBConnection.getConnection()) {

            TransactionInfo tx =
                    loadTransaction(
                            con,
                            txnId,
                            sellerId
                    );

            if (tx == null
                    || tx.sellerId != sellerId) {

                response.sendRedirect("transactions");
                return;
            }

            String otp =
                    String.format(
                            "%06d",
                            random.nextInt(1000000)
                    );

            String sql =
                    "UPDATE transaction_settlements ts " +
                    "JOIN transactions t " +
                    "ON t.txn_id = ts.txn_id " +
                    "SET ts.handover_code = ? " +
                    "WHERE ts.txn_id = ? " +
                    "AND t.seller_id = ? " +
                    "AND t.payment_status = 'SUCCESS' " +
                    "AND ts.pickup_status = 'SCHEDULED' " +
                    "AND ts.settlement_status = 'PICKUP_SCHEDULED'";

            try (PreparedStatement ps =
                         con.prepareStatement(sql)) {

                ps.setString(1, otp);
                ps.setInt(2, txnId);
                ps.setInt(3, sellerId);

                int updated =
                        ps.executeUpdate();

                if (updated != 1) {

                    response.getWriter().print(
                            Page.message(
                                    request,
                                    "OTP unavailable",
                                    "OTP cannot be generated yet",
                                    "The buyer must arrange the pickup first.",
                                    "handover?txnId=" + txnId,
                                    "Back",
                                    "warn"
                            )
                    );

                    return;
                }
            }

            response.sendRedirect(
                    "handover?txnId=" + txnId
            );

        } catch (SQLException e) {

            response.getWriter().print(
                    Page.message(
                            request,
                            "OTP error",
                            "Could not generate OTP",
                            "Please try again.",
                            "handover?txnId=" + txnId,
                            "Back",
                            "error"
                    )
            );
        }
    }

    /*
     * ================================================================
     * VERIFY OTP
     * ================================================================
     */

    private void verifyOtp(
            HttpServletRequest request,
            HttpServletResponse response,
            int buyerId,
            int txnId)
            throws IOException {

        String enteredOtp =
                clean(
                        request.getParameter(
                                "handoverOtp"
                        )
                );

        if (enteredOtp.isEmpty()) {

            response.getWriter().print(
                    Page.message(
                            request,
                            "OTP required",
                            "Enter the handover OTP",
                            "Ask the seller for the transaction OTP after receiving the product.",
                            "handover?txnId=" + txnId,
                            "Back",
                            "warn"
                    )
            );

            return;
        }

        try (Connection con =
                     DBConnection.getConnection()) {

            con.setAutoCommit(false);

            /*
             * Lock transaction + settlement.
             */
            String sql =
                    "SELECT " +
                    "t.buyer_id, " +
                    "t.seller_id, " +
                    "t.amount, " +
                    "t.payment_status, " +
                    "ts.settlement_status, " +
                    "ts.pickup_status, " +
                    "ts.handover_code, " +
                    "ts.protection_amount " +

                    "FROM transactions t " +

                    "JOIN transaction_settlements ts " +
                    "ON ts.txn_id = t.txn_id " +

                    "WHERE t.txn_id = ? " +
                    "AND t.buyer_id = ? " +

                    "FOR UPDATE";

            int sellerId;
            BigDecimal amount;
            BigDecimal existingProtection;
            String paymentStatus;
            String settlementStatus;
            String pickupStatus;
            String storedOtp;

            try (PreparedStatement ps =
                         con.prepareStatement(sql)) {

                ps.setInt(1, txnId);
                ps.setInt(2, buyerId);

                try (ResultSet rs =
                             ps.executeQuery()) {

                    if (!rs.next()) {

                        con.rollback();

                        response.sendRedirect(
                                "transactions"
                        );

                        return;
                    }

                    sellerId =
                            rs.getInt("seller_id");

                    amount =
                            rs.getBigDecimal("amount");

                    paymentStatus =
                            rs.getString(
                                    "payment_status"
                            );

                    settlementStatus =
                            rs.getString(
                                    "settlement_status"
                            );

                    pickupStatus =
                            rs.getString(
                                    "pickup_status"
                            );

                    storedOtp =
                            rs.getString(
                                    "handover_code"
                            );

                    existingProtection =
                            rs.getBigDecimal(
                                    "protection_amount"
                            );
                }
            }

            if (!"SUCCESS".equalsIgnoreCase(
                    paymentStatus
            )) {

                con.rollback();

                showError(
                        request,
                        response,
                        txnId,
                        "Payment is not successful",
                        "The handover cannot be verified."
                );

                return;
            }

            if (!"PICKUP_SCHEDULED".equalsIgnoreCase(
                    settlementStatus
            )
                    || !"SCHEDULED".equalsIgnoreCase(
                    pickupStatus
            )) {

                con.rollback();

                showError(
                        request,
                        response,
                        txnId,
                        "Pickup is not scheduled",
                        "Arrange the pickup before verifying the handover."
                );

                return;
            }

            if (storedOtp == null
                    || storedOtp.trim().isEmpty()) {

                con.rollback();

                showError(
                        request,
                        response,
                        txnId,
                        "OTP not generated",
                        "Ask the seller to generate the handover OTP."
                );

                return;
            }

            if (!storedOtp.equals(
                    enteredOtp
            )) {

                con.rollback();

                showError(
                        request,
                        response,
                        txnId,
                        "Incorrect OTP",
                        "The handover OTP does not match."
                );

                return;
            }

            /*
             * ---------------------------------------------------------
             * 90% SELLER RELEASE
             * 10% PROTECTION HOLD
             * ---------------------------------------------------------
             */

            BigDecimal sellerRelease =
                    amount
                            .multiply(
                                    SELLER_RELEASE_PERCENT
                            )
                            .setScale(
                                    2,
                                    RoundingMode.HALF_UP
                            );

            BigDecimal protection =
                    amount
                            .subtract(
                                    sellerRelease
                            )
                            .setScale(
                                    2,
                                    RoundingMode.HALF_UP
                            );

            /*
             * Protect against accidental duplicate credit.
             */
            if (existingProtection != null
                    && existingProtection.compareTo(
                    BigDecimal.ZERO
            ) > 0) {

                con.rollback();

                showError(
                        request,
                        response,
                        txnId,
                        "Handover already processed",
                        "This transaction has already received its seller release."
                );

                return;
            }

            /*
             * Credit seller with 90%.
             */
            String creditSeller =
                    "UPDATE students " +
                    "SET wallet_balance = " +
                    "wallet_balance + ? " +
                    "WHERE student_id = ?";

            try (PreparedStatement ps =
                         con.prepareStatement(
                                 creditSeller
                         )) {

                ps.setBigDecimal(
                        1,
                        sellerRelease
                );

                ps.setInt(
                        2,
                        sellerId
                );

                if (ps.executeUpdate() != 1) {

                    con.rollback();

                    showError(
                            request,
                            response,
                            txnId,
                            "Seller wallet error",
                            "The seller wallet could not be updated."
                    );

                    return;
                }
            }

            /*
             * Protection period.
             */
            Timestamp protectionUntil =
                    Timestamp.valueOf(
                            LocalDateTime.now()
                                    .plusHours(
                                            PROTECTION_HOURS
                                    )
                    );

            String updateSettlement =
                    "UPDATE transaction_settlements " +
                    "SET seller_released_amount = ?, " +
                    "protection_amount = ?, " +
                    "settlement_status = 'PROTECTION_HOLD', " +
                    "pickup_status = 'COMPLETED', " +
                    "handover_verified_at = NOW(), " +
                    "protection_until = ?, " +
                    "seller_final_release_at = ? " +
                    "WHERE txn_id = ? " +
                    "AND settlement_status = 'PICKUP_SCHEDULED'";

            try (PreparedStatement ps =
                         con.prepareStatement(
                                 updateSettlement
                         )) {

                ps.setBigDecimal(
                        1,
                        sellerRelease
                );

                ps.setBigDecimal(
                        2,
                        protection
                );

                ps.setTimestamp(
                        3,
                        protectionUntil
                );

                ps.setTimestamp(
                        4,
                        protectionUntil
                );

                ps.setInt(
                        5,
                        txnId
                );

                if (ps.executeUpdate() != 1) {

                    con.rollback();

                    showError(
                            request,
                            response,
                            txnId,
                            "Settlement update failed",
                            "The handover could not be completed."
                    );

                    return;
                }
            }

            con.commit();

            response.sendRedirect(
                    "handover?txnId=" + txnId
            );

        } catch (SQLException e) {

            response.getWriter().print(
                    Page.message(
                            request,
                            "Handover error",
                            "Could not verify the handover",
                            "Please try again.",
                            "handover?txnId=" + txnId,
                            "Back",
                            "error"
                    )
            );
        }
    }

    /*
     * ================================================================
     * AUTOMATIC FINAL PROTECTION RELEASE
     * ================================================================
     *
     * This is a lazy release:
     * when someone opens the handover page after the protection
     * deadline, the remaining amount is released automatically.
     *
     * No background scheduler is required.
     */

    private void releaseProtectionIfEligible(
            Connection con,
            int txnId)
            throws SQLException {

        con.setAutoCommit(false);

        try {

            String lockSql =
                    "SELECT " +
                    "t.seller_id, " +
                    "t.payment_status, " +
                    "ts.protection_amount, " +
                    "ts.seller_released_amount, " +
                    "ts.settlement_status, " +
                    "ts.protection_until " +

                    "FROM transactions t " +

                    "JOIN transaction_settlements ts " +
                    "ON ts.txn_id = t.txn_id " +

                    "WHERE t.txn_id = ? " +

                    "FOR UPDATE";

            int sellerId;
            BigDecimal protectionAmount;
            BigDecimal sellerReleased;
            String paymentStatus;
            String settlementStatus;
            Timestamp protectionUntil;

            try (PreparedStatement ps =
                         con.prepareStatement(
                                 lockSql
                         )) {

                ps.setInt(1, txnId);

                try (ResultSet rs =
                             ps.executeQuery()) {

                    if (!rs.next()) {
                        con.rollback();
                        return;
                    }

                    sellerId =
                            rs.getInt("seller_id");

                    paymentStatus =
                            rs.getString(
                                    "payment_status"
                            );

                    protectionAmount =
                            rs.getBigDecimal(
                                    "protection_amount"
                            );

                    sellerReleased =
                            rs.getBigDecimal(
                                    "seller_released_amount"
                            );

                    settlementStatus =
                            rs.getString(
                                    "settlement_status"
                            );

                    protectionUntil =
                            rs.getTimestamp(
                                    "protection_until"
                            );
                }
            }

            if (!"SUCCESS".equalsIgnoreCase(
                    paymentStatus
            )
                    || !"PROTECTION_HOLD".equalsIgnoreCase(
                    settlementStatus
            )
                    || protectionAmount == null
                    || protectionAmount.compareTo(
                    BigDecimal.ZERO
            ) <= 0
                    || protectionUntil == null
                    || protectionUntil.after(
                    new Timestamp(
                            System.currentTimeMillis()
                    )
            )) {

                con.rollback();
                return;
            }

            /*
             * Release remaining 10%.
             */

            String credit =
                    "UPDATE students " +
                    "SET wallet_balance = " +
                    "wallet_balance + ? " +
                    "WHERE student_id = ?";

            try (PreparedStatement ps =
                         con.prepareStatement(
                                 credit
                         )) {

                ps.setBigDecimal(
                        1,
                        protectionAmount
                );

                ps.setInt(
                        2,
                        sellerId
                );

                if (ps.executeUpdate() != 1) {

                    con.rollback();
                    return;
                }
            }

            BigDecimal finalSellerAmount =
                    sellerReleased.add(
                            protectionAmount
                    );

            String complete =
                    "UPDATE transaction_settlements " +
                    "SET seller_released_amount = ?, " +
                    "protection_amount = 0.00, " +
                    "settlement_status = 'COMPLETED', " +
                    "seller_final_release_at = NOW() " +
                    "WHERE txn_id = ? " +
                    "AND settlement_status = 'PROTECTION_HOLD' " +
                    "AND protection_amount > 0";

            try (PreparedStatement ps =
                         con.prepareStatement(
                                 complete
                         )) {

                ps.setBigDecimal(
                        1,
                        finalSellerAmount
                );

                ps.setInt(
                        2,
                        txnId
                );

                if (ps.executeUpdate() != 1) {

                    con.rollback();
                    return;
                }
            }

            con.commit();

        } catch (SQLException e) {

            try {
                con.rollback();
            } catch (SQLException ignored) {
            }

            throw e;
        }
    }

    /*
     * ================================================================
     * LOAD TRANSACTION
     * ================================================================
     */

    private TransactionInfo loadTransaction(
            Connection con,
            int txnId,
            int userId)
            throws SQLException {

        String sql =
                "SELECT " +

                "t.txn_id, " +
                "t.order_ref, " +
                "t.buyer_id, " +
                "t.seller_id, " +
                "t.amount, " +
                "t.payment_status, " +

                "l.title, " +

                "buyer.name AS buyer_name, " +
                "buyer.email AS buyer_email, " +
                "buyer.phone AS buyer_phone, " +

                "seller.name AS seller_name, " +
                "seller.email AS seller_email, " +
                "seller.phone AS seller_phone " +

                "FROM transactions t " +

                "JOIN listings l " +
                "ON l.listing_id = t.listing_id " +

                "JOIN students buyer " +
                "ON buyer.student_id = t.buyer_id " +

                "JOIN students seller " +
                "ON seller.student_id = t.seller_id " +

                "WHERE t.txn_id = ? " +
                "AND (t.buyer_id = ? OR t.seller_id = ?)";

        try (PreparedStatement ps =
                     con.prepareStatement(sql)) {

            ps.setInt(1, txnId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);

            try (ResultSet rs =
                         ps.executeQuery()) {

                if (!rs.next()) {
                    return null;
                }

                TransactionInfo tx =
                        new TransactionInfo();

                tx.txnId =
                        rs.getInt("txn_id");

                tx.orderRef =
                        rs.getString("order_ref");

                tx.buyerId =
                        rs.getInt("buyer_id");

                tx.sellerId =
                        rs.getInt("seller_id");

                tx.amount =
                        rs.getBigDecimal("amount");

                tx.paymentStatus =
                        rs.getString(
                                "payment_status"
                        );

                tx.title =
                        rs.getString("title");

                tx.buyerName =
                        rs.getString("buyer_name");

                tx.buyerEmail =
                        rs.getString("buyer_email");

                tx.buyerPhone =
                        rs.getString("buyer_phone");

                tx.sellerName =
                        rs.getString("seller_name");

                tx.sellerEmail =
                        rs.getString("seller_email");

                tx.sellerPhone =
                        rs.getString("seller_phone");

                return tx;
            }
        }
    }

    /*
     * ================================================================
     * LOAD SETTLEMENT
     * ================================================================
     */

    private SettlementInfo loadSettlement(
            Connection con,
            int txnId)
            throws SQLException {

        String sql =
                "SELECT " +

                "settlement_status, " +
                "pickup_status, " +
                "pickup_date, " +
                "pickup_time, " +
                "pickup_location, " +
                "handover_code, " +
                "seller_released_amount, " +
                "protection_amount, " +
                "protection_until, " +
                "handover_verified_at " +

                "FROM transaction_settlements " +

                "WHERE txn_id = ?";

        try (PreparedStatement ps =
                     con.prepareStatement(sql)) {

            ps.setInt(1, txnId);

            try (ResultSet rs =
                         ps.executeQuery()) {

                if (!rs.next()) {
                    return null;
                }

                SettlementInfo s =
                        new SettlementInfo();

                s.settlementStatus =
                        rs.getString(
                                "settlement_status"
                        );

                s.pickupStatus =
                        rs.getString(
                                "pickup_status"
                        );

                s.pickupDate =
                        rs.getDate(
                                "pickup_date"
                        );

                s.pickupTime =
                        rs.getString(
                                "pickup_time"
                        );

                s.pickupLocation =
                        rs.getString(
                                "pickup_location"
                        );

                s.handoverCode =
                        rs.getString(
                                "handover_code"
                        );

                s.sellerReleasedAmount =
                        rs.getBigDecimal(
                                "seller_released_amount"
                        );

                s.protectionAmount =
                        rs.getBigDecimal(
                                "protection_amount"
                        );

                s.protectionUntil =
                        rs.getTimestamp(
                                "protection_until"
                        );

                s.handoverVerifiedAt =
                        rs.getTimestamp(
                                "handover_verified_at"
                        );

                return s;
            }
        }
    }

    /*
     * ================================================================
     * PICKUP FORM
     * ================================================================
     */

    private void renderPickupForm(
            PrintWriter out,
            int txnId) {

        out.print(
                "<section class=\"glass\" " +
                "style=\"margin-top:1.5rem;padding:1.5rem;\">" +

                "<h2>Pickup arrangement</h2>" +

                "<p class=\"muted\">" +
                "Choose when and where you want to meet the seller " +
                "on campus." +
                "</p>" +

                "<form method=\"post\" action=\"handover\">" +

                "<input type=\"hidden\" " +
                "name=\"txnId\" value=\"" +
                txnId +
                "\">" +

                "<input type=\"hidden\" " +
                "name=\"action\" value=\"arrange\">" +

                "<div class=\"form-grid\">"
        );

        out.print(
                "<label>" +
                "Pickup date" +
                "<input type=\"date\" " +
                "name=\"pickupDate\" required>" +
                "</label>"
        );

        out.print(
                "<label>" +
                "Pickup time" +
                "<input type=\"time\" " +
                "name=\"pickupTime\" required>" +
                "</label>"
        );

        out.print(
                "<label style=\"grid-column:1/-1;\">" +
                "Campus pickup location" +
                "<input type=\"text\" " +
                "name=\"pickupLocation\" " +
                "maxlength=\"255\" " +
                "placeholder=\"Example: Library entrance\" " +
                "required>" +
                "</label>"
        );

        out.print(
                "</div>" +

                "<button class=\"btn btn-primary\" " +
                "type=\"submit\">" +

                "Arrange pickup" +

                "</button>" +

                "</form>" +

                "</section>"
        );
    }

    /*
     * ================================================================
     * SELLER OTP
     * ================================================================
     */

    private void renderSellerOtpSection(
            PrintWriter out,
            int txnId,
            String existingOtp) {

        out.print(
                "<section class=\"glass\" " +
                "style=\"margin-top:1.5rem;padding:1.5rem;\">" +

                "<h2>Handover OTP</h2>" +

                "<p class=\"muted\">" +
                "Generate the transaction-specific OTP and share it " +
                "with the buyer only after handing over the product." +
                "</p>"
        );

        if (existingOtp == null
                || existingOtp.trim().isEmpty()) {

            out.print(
                    "<form method=\"post\" action=\"handover\">" +

                    "<input type=\"hidden\" " +
                    "name=\"txnId\" value=\"" +
                    txnId +
                    "\">" +

                    "<input type=\"hidden\" " +
                    "name=\"action\" value=\"generateOtp\">" +

                    "<button class=\"btn btn-primary\" " +
                    "type=\"submit\">" +

                    "Generate Handover OTP" +

                    "</button>" +

                    "</form>"
            );

        } else {

            out.print(
                    "<div style=\"padding:1rem;" +
                    "border-radius:16px;" +
                    "background:rgba(82,230,193,.08);" +
                    "border:1px solid rgba(82,230,193,.25);\">" +

                    "<strong>Handover OTP:</strong>" +

                    "<div style=\"font-size:2rem;" +
                    "font-weight:800;" +
                    "letter-spacing:.35rem;" +
                    "margin-top:.5rem;\">" +

                    Page.esc(existingOtp) +

                    "</div>" +

                    "<p class=\"muted\">" +
                    "Give this code to the buyer after " +
                    "the product has been handed over." +
                    "</p>" +

                    "</div>"
            );
        }

        out.print("</section>");
    }

    /*
     * ================================================================
     * BUYER OTP
     * ================================================================
     */

    private void renderOtpVerification(
            PrintWriter out,
            int txnId) {

        out.print(
                "<section class=\"glass\" " +
                "style=\"margin-top:1.5rem;padding:1.5rem;\">" +

                "<h2>Confirm handover</h2>" +

                "<p class=\"muted\">" +
                "After receiving and checking the product, " +
                "enter the OTP provided by the seller." +
                "</p>" +

                "<form method=\"post\" action=\"handover\">" +

                "<input type=\"hidden\" " +
                "name=\"txnId\" value=\"" +
                txnId +
                "\">" +

                "<input type=\"hidden\" " +
                "name=\"action\" value=\"verifyOtp\">" +

                "<label>" +
                "Handover OTP" +

                "<input type=\"text\" " +
                "name=\"handoverOtp\" " +
                "maxlength=\"6\" " +
                "pattern=\"[0-9]{6}\" " +
                "inputmode=\"numeric\" " +
                "placeholder=\"Enter 6-digit OTP\" " +
                "required>" +

                "</label>" +

                "<button class=\"btn btn-primary\" " +
                "type=\"submit\">" +

                "Confirm Handover" +

                "</button>" +

                "</form>" +

                "</section>"
        );
    }

    /*
     * ================================================================
     * INFO ROW
     * ================================================================
     */

    private void infoRow(
            PrintWriter out,
            String label,
            String value) {

        out.print(
                "<div style=\"display:flex;" +
                "justify-content:space-between;" +
                "gap:1rem;" +
                "padding:.65rem 0;" +
                "border-bottom:1px solid rgba(255,255,255,.06);\">" +

                "<span class=\"muted\">" +
                Page.esc(label) +
                "</span>" +

                "<strong>" +
                Page.esc(value == null ? "—" : value) +
                "</strong>" +

                "</div>"
        );
    }

    /*
     * ================================================================
     * ERROR
     * ================================================================
     */

    private void showError(
            HttpServletRequest request,
            HttpServletResponse response,
            int txnId,
            String heading,
            String description)
            throws IOException {

        response.getWriter().print(
                Page.message(
                        request,
                        "Handover",
                        heading,
                        description,
                        "handover?txnId=" + txnId,
                        "Back to handover",
                        "warn"
                )
        );
    }

    /*
     * ================================================================
     * CLEAN
     * ================================================================
     */

    private String clean(String value) {

        if (value == null) {
            return "";
        }

        return value.trim();
    }

    /*
     * ================================================================
     * TRANSACTION DTO
     * ================================================================
     */

    private static class TransactionInfo {

        int txnId;

        String orderRef;

        int buyerId;

        int sellerId;

        BigDecimal amount;

        String paymentStatus;

        String title;

        String buyerName;

        String buyerEmail;

        String buyerPhone;

        String sellerName;

        String sellerEmail;

        String sellerPhone;
    }

    /*
     * ================================================================
     * SETTLEMENT DTO
     * ================================================================
     */

    private static class SettlementInfo {

        String settlementStatus;

        String pickupStatus;

        Date pickupDate;

        String pickupTime;

        String pickupLocation;

        String handoverCode;

        BigDecimal sellerReleasedAmount;

        BigDecimal protectionAmount;

        Timestamp protectionUntil;

        Timestamp handoverVerifiedAt;
    }
}