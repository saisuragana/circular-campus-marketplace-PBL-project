import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;

@WebServlet("/wallet")
public class WalletServlet extends HttpServlet {

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        HttpSession session = request.getSession(false);

        if (session == null ||
            session.getAttribute("studentId") == null) {

            response.sendRedirect("login.html");
            return;
        }

        response.setContentType("text/html;charset=UTF-8");

        Object balanceObj =
                session.getAttribute("walletBalance");

        BigDecimal balance =
                balanceObj instanceof BigDecimal
                        ? (BigDecimal) balanceObj
                        : BigDecimal.ZERO;

        StringBuilder html = new StringBuilder();

        html.append(Page.header(request, "Campus Wallet", "wallet"));

        html.append(
                "<main class=\"page wallet-page\">" +

                "<section class=\"wallet-card glass\">" +

                "<p class=\"eyebrow\">CAMPUS WALLET</p>" +

                "<h1>Manage your wallet</h1>" +

                "<p class=\"wallet-balance\">₹")
                .append(Page.money(balance))
                .append("</p>" +

                "<p class=\"muted\">" +
                "Use your campus wallet to purchase items " +
                "from other students." +
                "</p>" +

                "<form action=\"wallet\" method=\"POST\" " +
                "class=\"wallet-topup-form\">" +

                "<h2>Add money</h2>" +

                "<div class=\"wallet-options\">" +

                "<button type=\"submit\" name=\"amount\" value=\"100\" " +
                "class=\"btn btn-ghost\">+ ₹100</button>" +

                "<button type=\"submit\" name=\"amount\" value=\"500\" " +
                "class=\"btn btn-ghost\">+ ₹500</button>" +

                "<button type=\"submit\" name=\"amount\" value=\"1000\" " +
                "class=\"btn btn-ghost\">+ ₹1,000</button>" +

                "</div>" +

                "<label class=\"field\">" +
                "<span>Custom amount</span>" +
                "<input type=\"number\" name=\"customAmount\" " +
                "min=\"1\" step=\"1\" " +
                "placeholder=\"Enter amount\">" +
                "</label>" +

                "<button type=\"submit\" " +
                "class=\"btn btn-primary\">" +
                "Add Money" +
                "</button>" +

                "</form>" +

                "<div class=\"wallet-note\">" +
                "Demo campus wallet for the PBL prototype. " +
                "No real payment is processed." +
                "</div>" +

                "</section>" +

                "</main>"
        );

        html.append(Page.footer());

        response.getWriter().print(html.toString());
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        HttpSession session =
                request.getSession(false);

        if (session == null ||
            session.getAttribute("studentId") == null) {

            response.sendRedirect("login.html");
            return;
        }

        int studentId =
                ((Number) session.getAttribute("studentId")).intValue();

        BigDecimal amount = null;

        try {

            String custom =
                    request.getParameter("customAmount");

            if (custom != null &&
                !custom.trim().isEmpty()) {

                amount =
                        new BigDecimal(custom.trim());

            } else {

                String preset =
                        request.getParameter("amount");

                if (preset != null) {
                    amount =
                            new BigDecimal(preset);
                }
            }

            if (amount == null ||
                amount.signum() <= 0 ||
                amount.compareTo(new BigDecimal("50000")) > 0) {

                session.setAttribute(
                        "flash",
                        "Enter an amount between ₹1 and ₹50,000."
                );

                response.sendRedirect("wallet");
                return;
            }

            try (Connection con =
                         DBConnection.getConnection()) {

                String sql =
                        "UPDATE students " +
                        "SET wallet_balance = " +
                        "wallet_balance + ? " +
                        "WHERE student_id = ?";

                try (PreparedStatement ps =
                             con.prepareStatement(sql)) {

                    ps.setBigDecimal(1, amount);
                    ps.setInt(2, studentId);

                    ps.executeUpdate();
                }
            }

            session.setAttribute(
                    "flash",
                    "₹" + Page.money(amount) +
                    " added to your Campus Wallet."
            );

            response.sendRedirect("wallet");

        } catch (Exception e) {

            e.printStackTrace();

            session.setAttribute(
                    "flash",
                    "Unable to add money right now."
            );

            response.sendRedirect("wallet");
        }
    }
}