import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.*;

@WebServlet("/cart")
public class CartServlet extends HttpServlet {

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

        Integer studentId =
                CartUtil.studentId(request);

        boolean loggedIn =
                studentId != null;

        String sql =
                "SELECT c.cart_id, c.quantity, "
              + "l.listing_id, l.title, l.category, "
              + "l.price, l.`condition`, l.status, "
              + "l.quantity AS stock, "
              + "l.seller_id, s.name AS seller_name, "
              + "(SELECT MIN(li.file_name) FROM listing_images li "
              + "WHERE li.listing_id = l.listing_id) AS image_file "
              + "FROM cart_items c "
              + "JOIN listings l "
              + "ON l.listing_id = c.listing_id "
              + "JOIN students s "
              + "ON s.student_id = l.seller_id "
              + "WHERE "
              + (loggedIn
                    ? "c.student_id = ?"
                    : "c.student_id IS NULL "
                      + "AND c.session_id = ?")
              + " ORDER BY c.added_at DESC";

        try (
                Connection con =
                        DBConnection.getConnection();

                PreparedStatement ps =
                        con.prepareStatement(sql)
        ) {

            if (loggedIn) {

                ps.setInt(
                        1,
                        studentId
                );

            } else {

                ps.setString(
                        1,
                        CartUtil.guestKey(request)
                );
            }

            StringBuilder rows =
                    new StringBuilder();

            BigDecimal total =
                    BigDecimal.ZERO;

            int lines = 0;

            boolean anyProblem =
                    false;

            try (ResultSet rs =
                         ps.executeQuery()) {

                while (rs.next()) {

                    lines++;

                    int cartId =
                            rs.getInt("cart_id");

                    int wanted =
                            rs.getInt("quantity");

                    int stock =
                            rs.getInt("stock");

                    String status =
                            rs.getString("status");

                    BigDecimal price =
                            rs.getBigDecimal("price");

                    boolean ok =
                            "Available"
                                    .equalsIgnoreCase(status)
                            && stock >= wanted;

                    if (!ok) {
                        anyProblem = true;
                    }

                    BigDecimal subtotal =
                            price.multiply(
                                    BigDecimal.valueOf(
                                            wanted
                                    )
                            );

                    if (ok) {
                        total =
                                total.add(subtotal);
                    }

                    rows.append(
                            "<li class=\"cart-row")
                        .append(
                                ok
                                        ? ""
                                        : " is-blocked"
                        )
                        .append("\">");
                    String cartImage = rs.getString("image_file");
                    rows.append("<span class=\"cart-icon\">");
                    if (cartImage != null && !cartImage.trim().isEmpty()) {
                        rows.append("<img class=\"cart-product-thumb\" src=\"product-image?file=")
                           .append(Page.esc(cartImage))
                           .append("\" alt=\"")
                           .append(Page.esc(rs.getString("title")))
                           .append("\" loading=\"lazy\">");
                    } else {
                        rows.append(Icons.forCategory(rs.getString("category")));
                    }
                    rows.append("</span>");

                    rows.append(
                            "<div class=\"cart-main\">"
                    );

                    rows.append(
                            "<a class=\"cart-title\" "
                            + "href=\"view-item?id="
                    )
                    .append(
                            rs.getInt(
                                    "listing_id"
                            )
                    )
                    .append("\">")
                    .append(
                            Page.esc(
                                    rs.getString(
                                            "title"
                                    )
                            )
                    )
                    .append("</a>");

                    rows.append(
                            "<p class=\"muted\">"
                    )
                    .append(
                            Page.esc(
                                    rs.getString(
                                            "condition"
                                    )
                            )
                    )
                    .append(
                            " &middot; sold by "
                    )
                    .append(
                            Page.esc(
                                    rs.getString(
                                            "seller_name"
                                    )
                            )
                    )
                    .append("</p>");

                    if (!ok) {

                        rows.append(
                                "<p class=\"warn-text\">"
                                + "Only "
                        )
                        .append(stock)
                        .append(
                                " left in stock. "
                                + "Update or remove this item."
                        )
                        .append("</p>");
                    }

                    rows.append(
                            "</div>"
                    );

                    rows.append(
                            "<span class=\"cart-qty\">"
                            + "&#8377;"
                    )
                    .append(
                            Page.money(price)
                    )
                    .append(
                            " &times; "
                    )
                    .append(wanted)
                    .append("</span>");

                    rows.append(
                            "<span class=\"cart-sub\">"
                            + "&#8377;"
                    )
                    .append(
                            Page.money(subtotal)
                    )
                    .append("</span>");

                    rows.append(
                            "<form action=\"remove-from-cart\" "
                            + "method=\"POST\">"
                    );

                    rows.append(
                            "<input type=\"hidden\" "
                            + "name=\"cartId\" value=\""
                    )
                    .append(cartId)
                    .append("\">");

                    rows.append(
                            "<button class=\"btn btn-ghost "
                            + "btn-sm\" type=\"submit\">"
                            + "Remove</button>"
                    );

                    rows.append(
                            "</form></li>"
                    );
                }
            }

            out.print(
                    Page.header(
                            request,
                            "Cart",
                            "cart"
                    )
            );

            out.print(
                    "<section class=\"cart-page\">"
                    + "<h1>Your cart</h1>"
            );

            if (lines == 0) {

                out.print(
                        "<div class=\"empty glass\">"
                        + "<h3>Your cart is empty</h3>"
                        + "<p>Add something from the marketplace "
                        + "and it will wait here.</p>"
                        + "<div class=\"empty-actions\">"
                        + "<a class=\"btn btn-primary\" "
                        + "href=\"marketplace\">"
                        + "Browse marketplace"
                        + "</a>"
                        + "</div>"
                        + "</div>"
                );

            } else {

                out.print(
                        "<div class=\"cart-layout\">"
                );

                out.print(
                        "<ul class=\"cart-list glass\">"
                                + rows
                                + "</ul>"
                );

                out.print(
                        summary(
                                request,
                                con,
                                total,
                                anyProblem,
                                loggedIn,
                                studentId
                        )
                );

                out.print(
                        "</div>"
                );
            }

            if (!loggedIn && lines > 0) {

                out.print(
                        "<p class=\"muted "
                        + "cart-guest-note\">"
                        + "You are browsing as a guest. "
                        + "Your cart will move into your account "
                        + "when you log in."
                        + "</p>"
                );
            }

            out.print("</section>");

            out.print(
                    Page.footer()
            );

        } catch (SQLException e) {

            System.err.println(
                    "[CartServlet] "
                            + e.getMessage()
            );

            out.print(
                    Page.message(
                            request,
                            "Cart unavailable",
                            "We cannot load your cart",
                            "Start MySQL in XAMPP and reload the page.",
                            "marketplace",
                            "Back to marketplace",
                            "error"
                    )
            );
        }
    }

    private String summary(
            HttpServletRequest request,
            Connection con,
            BigDecimal total,
            boolean problem,
            boolean loggedIn,
            Integer studentId)
            throws SQLException {

        StringBuilder sb =
                new StringBuilder();

        sb.append(
                "<aside class=\"cart-summary glass\">"
        );

        sb.append(
                "<h2>Order Summary</h2>"
        );

        sb.append(
                "<p class=\"sum-line\">"
                + "<span>Items total</span>"
                + "<strong>&#8377;"
        )
        .append(
                Page.money(total)
        )
        .append(
                "</strong></p>"
        );

        if (!loggedIn) {

            sb.append(
                    "<p class=\"muted\">"
                    + "Log in to continue to payment."
                    + "</p>"
            );

            sb.append(
                    "<a class=\"btn btn-primary btn-block\" "
                    + "href=\"login.html\">"
                    + "Log in to checkout"
                    + "</a>"
            );

        } else {

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
                        studentId
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

            sb.append(
                    "<p class=\"sum-line\">"
                    + "<span>Campus Wallet</span>"
                    + "<strong>&#8377;"
            )
            .append(
                    Page.money(wallet)
            )
            .append(
                    "</strong></p>"
            );

            sb.append(
                    "<p class=\"sum-line sum-total\">"
                    + "<span>Total</span>"
                    + "<strong>&#8377;"
            )
            .append(
                    Page.money(total)
            )
            .append(
                    "</strong></p>"
            );

            if (problem) {

                sb.append(
                        "<p class=\"warn-text\">"
                        + "Fix the highlighted stock issues "
                        + "before checkout."
                        + "</p>"
                );

            } else {

                sb.append(
                        "<form action=\"checkout\" "
                        + "method=\"POST\" "
                        + "class=\"payment-form\">"
                );

                sb.append(
                        "<h3>Choose payment method</h3>"
                );

                // WALLET
                sb.append(
                        "<label class=\"payment-option\">"
                        + "<input type=\"radio\" "
                        + "name=\"paymentMethod\" "
                        + "value=\"CAMPUS_WALLET\" "
                        + "checked "
                        + "onclick=\"showPayment('wallet')\">"
                        + "<span>"
                        + "<strong>Campus Wallet</strong>"
                        + "<small>Balance: &#8377;"
                )
                .append(
                        Page.money(wallet)
                )
                .append(
                        "</small>"
                        + "</span>"
                        + "</label>"
                );

                // UPI
                sb.append(
                        "<label class=\"payment-option\">"
                        + "<input type=\"radio\" "
                        + "name=\"paymentMethod\" "
                        + "value=\"UPI\" "
                        + "onclick=\"showPayment('upi')\">"
                        + "<span>"
                        + "<strong>UPI</strong>"
                        + "<small>Demo payment</small>"
                        + "</span>"
                        + "</label>"
                );

                // CARD
                sb.append(
                        "<label class=\"payment-option\">"
                        + "<input type=\"radio\" "
                        + "name=\"paymentMethod\" "
                        + "value=\"CARD\" "
                        + "onclick=\"showPayment('card')\">"
                        + "<span>"
                        + "<strong>Debit / Credit Card</strong>"
                        + "<small>Demo payment</small>"
                        + "</span>"
                        + "</label>"
                );

                // WALLET INFO
                sb.append(
                        "<div id=\"walletPayment\" "
                        + "class=\"payment-box\">"
                        + "<p class=\"muted\">"
                        + "Your Campus Wallet will be "
                        + "deducted after successful checkout."
                        + "</p>"
                        + "</div>"
                );

                // UPI
                sb.append(
                        "<div id=\"upiPayment\" "
                        + "class=\"payment-box\" "
                        + "style=\"display:none\">"
                        + "<label>UPI ID</label>"
                        + "<input class=\"input\" "
                        + "type=\"text\" "
                        + "name=\"upiId\" "
                        + "placeholder=\"student@upi\">"
                        + "<small class=\"muted\">"
                        + "Demo only. Example: student@upi"
                        + "</small>"
                        + "</div>"
                );

                // CARD
                sb.append(
                        "<div id=\"cardPayment\" "
                        + "class=\"payment-box\" "
                        + "style=\"display:none\">"

                        + "<label>Card Number</label>"
                        + "<input class=\"input\" "
                        + "type=\"text\" "
                        + "name=\"cardNumber\" "
                        + "maxlength=\"19\" "
                        + "placeholder=\"1234 5678 9012 3456\">"

                        + "<label>Expiry</label>"
                        + "<input class=\"input\" "
                        + "type=\"text\" "
                        + "name=\"cardExpiry\" "
                        + "placeholder=\"MM/YY\">"

                        + "<label>CVV</label>"
                        + "<input class=\"input\" "
                        + "type=\"password\" "
                        + "name=\"cardCvv\" "
                        + "maxlength=\"4\" "
                        + "placeholder=\"123\">"

                        + "<small class=\"muted\">"
                        + "Demo only. Card details are never stored."
                        + "</small>"

                        + "</div>"
                );

                sb.append(
                        "<button class=\"btn btn-primary "
                        + "btn-block\" "
                        + "type=\"submit\">"
                        + "Pay &#8377;"
                )
                .append(
                        Page.money(total)
                )
                .append(
                        "</button>"
                );

                sb.append(
                        "</form>"
                );
            }
        }

        sb.append(
                "<a class=\"btn btn-ghost btn-block\" "
                + "href=\"marketplace\">"
                + "Keep browsing"
                + "</a>"
        );

        // Small JS only for payment UI.
        sb.append(
                "<script>"
                + "function showPayment(type){"
                + "document.getElementById('walletPayment').style.display="
                + "(type==='wallet'?'block':'none');"
                + "document.getElementById('upiPayment').style.display="
                + "(type==='upi'?'block':'none');"
                + "document.getElementById('cardPayment').style.display="
                + "(type==='card'?'block':'none');"
                + "}"
                + "</script>"
        );

        sb.append(
                "</aside>"
        );

        return sb.toString();
    }
}