import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

@WebServlet("/chat")
public class ChatServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

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
            txnId = Integer.parseInt(request.getParameter("txnId"));
        } catch (Exception e) {
            response.sendRedirect("transactions");
            return;
        }

        try (Connection con = DBConnection.getConnection()) {

            String transactionSql =
                    "SELECT t.txn_id, t.buyer_id, t.seller_id, " +
                    "l.title, " +
                    "b.name AS buyer_name, " +
                    "b.email AS buyer_email, " +
                    "s.name AS seller_name, " +
                    "s.email AS seller_email " +
                    "FROM transactions t " +
                    "JOIN listings l ON l.listing_id = t.listing_id " +
                    "JOIN students b ON b.student_id = t.buyer_id " +
                    "JOIN students s ON s.student_id = t.seller_id " +
                    "WHERE t.txn_id = ? " +
                    "AND (t.buyer_id = ? OR t.seller_id = ?)";

            int buyerId;
            int sellerId;
            String title;
            String buyerName;
            String sellerName;

            try (PreparedStatement ps =
                         con.prepareStatement(transactionSql)) {

                ps.setInt(1, txnId);
                ps.setInt(2, me);
                ps.setInt(3, me);

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {
                        outMessage(
                                request,
                                response,
                                "Chat unavailable",
                                "You are not a participant in this transaction.",
                                "transactions",
                                "Back to transactions"
                        );
                        return;
                    }

                    buyerId = rs.getInt("buyer_id");
                    sellerId = rs.getInt("seller_id");

                    title = rs.getString("title");
                    buyerName = rs.getString("buyer_name");
                    sellerName = rs.getString("seller_name");
                }
            }

            String messagesSql =
                    "SELECT m.message_id, m.sender_id, " +
                    "m.receiver_id, m.message, m.sent_at, " +
                    "s.name AS sender_name " +
                    "FROM transaction_messages m " +
                    "JOIN students s ON s.student_id = m.sender_id " +
                    "WHERE m.txn_id = ? " +
                    "ORDER BY m.sent_at ASC";

            PrintWriter out = response.getWriter();

            out.print(Page.header(
                    request,
                    "Chat",
                    "transactions"
            ));

            out.print("<main class=\"page-shell\">");

            out.print(
                    "<section class=\"page-hero\">" +
                    "<span class=\"eyebrow\">TRANSACTION CHAT</span>" +
                    "<h1>Chat with " +
                    Page.esc(me == sellerId
                            ? buyerName
                            : sellerName) +
                    "</h1>" +
                    "<p>" +
                    Page.esc(title) +
                    " · Transaction #" +
                    txnId +
                    "</p>" +
                    "</section>"
            );

            out.print(
                    "<section class=\"glass\" " +
                    "style=\"max-width:900px;margin:auto;padding:1.5rem;\">"
            );

            out.print(
                    "<div style=\"margin-bottom:1rem;\">" +
                    "<strong>Buyer:</strong> " +
                    Page.esc(buyerName) +
                    " &nbsp; " +
                    "<strong>Seller:</strong> " +
                    Page.esc(sellerName) +
                    "</div>"
            );

            out.print(
                    "<div id=\"chatBox\" " +
                    "style=\"height:420px;overflow-y:auto;" +
                    "padding:1rem;border-radius:16px;" +
                    "background:rgba(255,255,255,.04);" +
                    "border:1px solid rgba(255,255,255,.10);\">"
            );

            boolean hasMessages = false;

            try (PreparedStatement ps =
                         con.prepareStatement(messagesSql)) {

                ps.setInt(1, txnId);

                try (ResultSet rs = ps.executeQuery()) {

                    while (rs.next()) {

                        hasMessages = true;

                        int senderId = rs.getInt("sender_id");
                        String sender = rs.getString("sender_name");
                        String message = rs.getString("message");
                        Timestamp sentAt = rs.getTimestamp("sent_at");

                        boolean mine = senderId == me;

                        String align = mine
                                ? "margin-left:auto;"
                                : "margin-right:auto;";

                        String bubble =
                                mine
                                ? "background:linear-gradient(135deg,#35e0b0,#5ca9ff);color:#07111f;"
                                : "background:rgba(255,255,255,.09);color:#fff;";

                        out.print(
                                "<div style=\"" +
                                "max-width:75%;padding:.8rem 1rem;" +
                                "margin:.6rem 0;" +
                                "border-radius:16px;" +
                                align +
                                bubble +
                                "\">" +

                                "<div style=\"font-size:.8rem;font-weight:700;" +
                                "margin-bottom:.3rem;\">" +
                                Page.esc(mine ? "You" : sender) +
                                "</div>" +

                                "<div>" +
                                Page.esc(message) +
                                "</div>" +

                                "<div style=\"font-size:.7rem;opacity:.7;" +
                                "margin-top:.3rem;\">" +
                                Page.esc(String.valueOf(sentAt)) +
                                "</div>" +

                                "</div>"
                        );
                    }
                }
            }

            if (!hasMessages) {
                out.print(
                        "<div class=\"empty\" " +
                        "style=\"text-align:center;padding:4rem 1rem;\">" +
                        "<h3>No messages yet</h3>" +
                        "<p class=\"muted\">" +
                        "Start the conversation about pickup, " +
                        "timing or anything related to this transaction." +
                        "</p>" +
                        "</div>"
                );
            }

            out.print("</div>");

            out.print(
                    "<form method=\"post\" action=\"chat\" " +
                    "style=\"display:flex;gap:.75rem;margin-top:1rem;\">"
            );

            out.print(
                    "<input type=\"hidden\" name=\"txnId\" value=\"" +
                    txnId +
                    "\">"
            );

            out.print(
                    "<input type=\"text\" " +
                    "name=\"message\" " +
                    "maxlength=\"1000\" " +
                    "required " +
                    "placeholder=\"Type your message...\" " +
                    "style=\"flex:1;\">"
            );

            out.print(
                    "<button class=\"btn btn-primary\" " +
                    "type=\"submit\">Send</button>"
            );

            out.print("</form>");

            out.print(
                    "<div style=\"display:flex;gap:.75rem;" +
                    "margin-top:1rem;\">" +

                    "<a class=\"btn btn-ghost\" " +
                    "href=\"handover?txnId=" +
                    txnId +
                    "\">Back to handover</a>" +

                    "<a class=\"btn btn-ghost\" " +
                    "href=\"transactions\">Transactions</a>" +

                    "</div>"
            );

            out.print("</section>");
            out.print("</main>");

            out.print(Page.footer());

        } catch (SQLException e) {

            response.getWriter().print(
                    Page.message(
                            request,
                            "Chat error",
                            "Could not load the conversation",
                            "Please try again.",
                            "transactions",
                            "Back",
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

        request.setCharacterEncoding("UTF-8");

        Integer me = CartUtil.studentId(request);

        if (me == null) {
            response.sendRedirect("login.html");
            return;
        }

        int txnId;

        try {
            txnId = Integer.parseInt(request.getParameter("txnId"));
        } catch (Exception e) {
            response.sendRedirect("transactions");
            return;
        }

        String message = request.getParameter("message");

        if (message == null) {
            response.sendRedirect("chat?txnId=" + txnId);
            return;
        }

        message = message.trim();

        if (message.isEmpty()) {
            response.sendRedirect("chat?txnId=" + txnId);
            return;
        }

        if (message.length() > 1000) {
            message = message.substring(0, 1000);
        }

        try (Connection con = DBConnection.getConnection()) {

            int buyerId;
            int sellerId;

            String sql =
                    "SELECT buyer_id, seller_id " +
                    "FROM transactions " +
                    "WHERE txn_id = ? " +
                    "AND (buyer_id = ? OR seller_id = ?)";

            try (PreparedStatement ps =
                         con.prepareStatement(sql)) {

                ps.setInt(1, txnId);
                ps.setInt(2, me);
                ps.setInt(3, me);

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {
                        response.sendRedirect("transactions");
                        return;
                    }

                    buyerId = rs.getInt("buyer_id");
                    sellerId = rs.getInt("seller_id");
                }
            }

            int receiverId =
                    me == buyerId
                            ? sellerId
                            : buyerId;

            String insert =
                    "INSERT INTO transaction_messages " +
                    "(txn_id, sender_id, receiver_id, message) " +
                    "VALUES (?, ?, ?, ?)";

            try (PreparedStatement ps =
                         con.prepareStatement(insert)) {

                ps.setInt(1, txnId);
                ps.setInt(2, me);
                ps.setInt(3, receiverId);
                ps.setString(4, message);

                ps.executeUpdate();
            }

            response.sendRedirect("chat?txnId=" + txnId);

        } catch (SQLException e) {

            response.getWriter().print(
                    Page.message(
                            request,
                            "Message failed",
                            "Could not send message",
                            "Please try again.",
                            "chat?txnId=" + txnId,
                            "Back to chat",
                            "error"
                    )
            );
        }
    }

    private void outMessage(
            HttpServletRequest request,
            HttpServletResponse response,
            String title,
            String heading,
            String link,
            String button)
            throws IOException {

        response.getWriter().print(
                Page.message(
                        request,
                        title,
                        heading,
                        "This conversation is only available to the buyer and seller.",
                        link,
                        button,
                        "warn"
                )
        );
    }
}