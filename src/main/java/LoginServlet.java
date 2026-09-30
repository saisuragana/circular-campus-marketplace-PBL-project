import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.sql.*;

/**
 * POST /login
 *
 * Checks the email + password against the students table, creates the
 * HttpSession and migrates any guest cart into the student's cart.
 */
@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    /** A GET on /login is not an error for the user - send them to the form. */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.sendRedirect("login.html");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        String email = trim(request.getParameter("email"));
        String password = request.getParameter("password");

        if (email.isEmpty() || password == null || password.isEmpty()) {
            fail(response, "Enter your email and password.");
            return;
        }

        String sql = "SELECT student_id, name, email, password, wallet_balance, verification_status "
                   + "FROM students WHERE email = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, email);

            int studentId;
            String name;
            BigDecimal wallet;

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !PasswordUtil.matches(password, rs.getString("password"))) {
                    // Same message for "no such email" and "wrong password"
                    // so the page does not reveal which emails exist.
                    fail(response, "That email and password do not match an account.");
                    return;
                }
                studentId = rs.getInt("student_id");
                name = rs.getString("name");
                wallet = rs.getBigDecimal("wallet_balance");
            }

            // Remember the guest session id, then start a fresh session
            // (a new session id after login avoids session fixation).
            HttpSession oldSession = request.getSession(true);
            String guestSessionId = oldSession.getId();
            oldSession.invalidate();

            HttpSession session = request.getSession(true);
            session.setAttribute("studentId", studentId);
            session.setAttribute("studentName", name);
            session.setAttribute("studentEmail", email);
            session.setAttribute("walletBalance", wallet);
            session.setMaxInactiveInterval(60 * 60);   // 1 hour
            // The password is never stored in the session.

            int moved = 0;
            try {
                moved = CartUtil.migrateGuestCart(con, guestSessionId, studentId);
            } catch (SQLException e) {
                System.err.println("[LoginServlet] cart migration failed: " + e.getMessage());
            }
            if (moved > 0) {
                session.setAttribute("flash", moved + " item(s) from your guest cart moved into your account.");
            }

            response.sendRedirect("marketplace");

        } catch (SQLException e) {
            System.err.println("[LoginServlet] " + e.getMessage());
            response.sendRedirect("login.html?error="
                    + URLEncoder.encode("The database is not reachable. Start MySQL in XAMPP and try again.", "UTF-8"));
        }
    }

    private void fail(HttpServletResponse response, String msg) throws IOException {
        response.sendRedirect("login.html?error=" + URLEncoder.encode(msg, "UTF-8"));
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
