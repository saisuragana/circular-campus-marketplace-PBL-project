import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.sql.*;

/**
 * POST /add-to-cart
 *
 * Guests may add items: the row is stored against their session id and is
 * merged into their account the moment they log in.
 *
 * Answers with JSON when the page asks for it (that is how the button
 * animates), otherwise falls back to a normal redirect so the site still
 * works with JavaScript switched off.
 */
@WebServlet("/add-to-cart")
public class AddToCartServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        // /add-to-cart is POST only; a stray GET just goes back to the marketplace.
        response.sendRedirect("marketplace");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        request.setCharacterEncoding("UTF-8");
        boolean wantsJson = wantsJson(request);

        int listingId;
        int qty;
        try {
            listingId = Integer.parseInt(request.getParameter("listingId"));
            String q = request.getParameter("quantity");
            qty = (q == null || q.trim().isEmpty()) ? 1 : Integer.parseInt(q.trim());
        } catch (NumberFormatException e) {
            finish(request, response, wantsJson, false, "That item could not be added.", 0);
            return;
        }
        if (qty < 1) qty = 1;

        try (Connection con = DBConnection.getConnection()) {

            String title;
            int available;
            int sellerId;
            String status;

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT title, quantity, seller_id, status FROM listings WHERE listing_id = ?")) {
                ps.setInt(1, listingId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        finish(request, response, wantsJson, false, "That listing no longer exists.", 0);
                        return;
                    }
                    title = rs.getString("title");
                    available = rs.getInt("quantity");
                    sellerId = rs.getInt("seller_id");
                    status = rs.getString("status");
                }
            }

            if (!"Available".equalsIgnoreCase(status) || available <= 0) {
                finish(request, response, wantsJson, false, "\"" + title + "\" is sold out.", CartUtil.countItems(request));
                return;
            }

            Integer me = CartUtil.studentId(request);
            if (me != null && me == sellerId) {
                finish(request, response, wantsJson, false, "You cannot buy your own listing.", CartUtil.countItems(request));
                return;
            }
            if (qty > available) qty = available;

            CartUtil.addOrIncrease(con, request, listingId, qty);
            finish(request, response, wantsJson, true, "Added to cart", CartUtil.countItems(request));

        } catch (SQLException e) {
            System.err.println("[AddToCartServlet] " + e.getMessage());
            finish(request, response, wantsJson, false, "The cart is unavailable right now.", 0);
        }
    }

    private boolean wantsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return "fetch".equals(request.getHeader("X-Requested-With"))
                || (accept != null && accept.contains("application/json"));
    }

    private void finish(HttpServletRequest request, HttpServletResponse response,
                        boolean json, boolean ok, String message, int count) throws IOException {
        if (json) {
            response.setContentType("application/json;charset=UTF-8");
            PrintWriter out = response.getWriter();
            out.print("{\"ok\":" + ok + ",\"count\":" + count
                    + ",\"message\":\"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
            return;
        }
        String next = request.getParameter("next");
        if (next != null && next.equals("cart")) {
            response.sendRedirect("cart");
        } else if (ok) {
            response.sendRedirect("cart");
        } else {
            response.sendRedirect("marketplace?msg=" + URLEncoder.encode(message, "UTF-8"));
        }
    }
}
