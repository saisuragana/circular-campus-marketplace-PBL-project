import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Demo Campus Wallet top-up page.
 * No real payment gateway is connected; the selected method is simulated.
 */
@WebServlet("/add-money")
public class AddMoneyServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Integer me = CartUtil.studentId(request);
        if (me == null) {
            response.getWriter().print(Page.message(
                    request, "Add money", "Log in to add money to your wallet",
                    "Your Campus Wallet is linked to your student account.",
                    "login.html", "Log in", "warn"));
            return;
        }

        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.print(Page.header(request, "Add Money", "wallet"));
        out.print("<section class=\"wallet-topup page-narrow\">");
        out.print("<div class=\"section-kicker\">CAMPUS WALLET</div>");
        out.print("<h1>Add money</h1>");
        out.print("<p class=\"lead muted\">Add demo funds to your Campus Wallet for testing purchases and checkout.</p>");
        out.print("<div class=\"topup-grid\">");
        out.print("<form class=\"glass topup-card\" method=\"POST\" action=\"add-money\">");
        out.print("<h2>Choose amount</h2>");
        out.print("<div class=\"amount-grid\">");
        out.print(amountButton("100"));
        out.print(amountButton("250"));
        out.print(amountButton("500"));
        out.print(amountButton("1000"));
        out.print("</div>");
        out.print("<label class=\"field\"><span>Custom amount</span><input name=\"amount\" type=\"number\" min=\"10\" max=\"50000\" step=\"1\" placeholder=\"Enter amount\" required></label>");
        out.print("<label class=\"field\"><span>Demo payment method</span><select name=\"method\"><option value=\"UPI_DEMO\">UPI (Demo)</option><option value=\"CARD_DEMO\">Debit / Credit Card (Demo)</option></select></label>");
        out.print("<button class=\"btn btn-primary topup-submit\" type=\"submit\">Add money to wallet</button>");
        out.print("<p class=\"form-note\">This is a PBL demo. No real bank/card/UPI transaction is performed.</p>");
        out.print("</form>");
        out.print("<aside class=\"glass wallet-topup-info\"><div class=\"wallet-icon\">" + Icons.svg("wallet") + "</div><h2>Campus Wallet</h2><p>Use the wallet for purchases inside Circular Campus. Sellers receive successful wallet payments in their wallet balance.</p><a class=\"btn btn-ghost\" href=\"transactions\">View transactions</a></aside>");
        out.print("</div></section>");
        out.print(Page.footer());
    }

    private String amountButton(String amount) {
        return "<button class=\"amount-chip\" type=\"button\" data-amount=\"" + amount + "\">&#8377;" + amount + "</button>";
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Integer me = CartUtil.studentId(request);
        if (me == null) {
            response.sendRedirect("login.html");
            return;
        }

        String raw = request.getParameter("amount");
        String method = request.getParameter("method");
        BigDecimal amount;
        try {
            amount = new BigDecimal(raw == null ? "" : raw.trim())
                    .setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            request.getSession().setAttribute("flash", "Enter a valid wallet amount.");
            response.sendRedirect("add-money");
            return;
        }

        if (amount.compareTo(new BigDecimal("10.00")) < 0 ||
                amount.compareTo(new BigDecimal("50000.00")) > 0) {
            request.getSession().setAttribute("flash", "Wallet top-up must be between ₹10 and ₹50,000.");
            response.sendRedirect("add-money");
            return;
        }

        if (!"UPI_DEMO".equals(method) && !"CARD_DEMO".equals(method)) {
            method = "UPI_DEMO";
        }

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE students SET wallet_balance = wallet_balance + ? WHERE student_id = ?")) {
            ps.setBigDecimal(1, amount);
            ps.setInt(2, me);
            int updated = ps.executeUpdate();
            if (updated != 1) throw new SQLException("Student wallet could not be updated.");

            BigDecimal newBalance = null;
            try (PreparedStatement q = con.prepareStatement(
                    "SELECT wallet_balance FROM students WHERE student_id = ?")) {
                q.setInt(1, me);
                try (java.sql.ResultSet rs = q.executeQuery()) {
                    if (rs.next()) newBalance = rs.getBigDecimal("wallet_balance");
                }
            }
            request.getSession().setAttribute("walletBalance", newBalance);
            request.getSession().setAttribute("flash", "₹" + Page.money(amount) + " added to your Campus Wallet using " +
                    ("UPI_DEMO".equals(method) ? "UPI (Demo)" : "Card (Demo)") + ".");
            response.sendRedirect("transactions");
        } catch (SQLException e) {
            System.err.println("[AddMoneyServlet] " + e.getMessage());
            response.getWriter().print(Page.message(
                    request, "Wallet", "Could not update your wallet",
                    "Check MySQL in XAMPP and try again.", "transactions", "Back to transactions", "error"));
        }
    }
}
