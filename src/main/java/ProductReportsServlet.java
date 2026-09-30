import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@WebServlet("/product-reports")
public class ProductReportsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws IOException {

        response.setContentType("text/html;charset=UTF-8");

        Integer me = CartUtil.studentId(request);

        if (me == null) {
            response.sendRedirect("login.html");
            return;
        }

        PrintWriter out = response.getWriter();

        try (Connection con = DBConnection.getConnection()) {

            out.print(Page.header(
                    request,
                    "Product Reports",
                    "transactions"
            ));

            out.print("<section class=\"txn-page\">");
            out.print("<div style=\"display:flex;justify-content:space-between;align-items:center;gap:1rem;flex-wrap:wrap;\">");
            out.print("<div>");
            out.print("<h1>Problem Reports</h1>");
            out.print("<p class=\"muted\">Reports you submitted for purchases or reports received for your listings.</p>");
            out.print("</div>");
            out.print("<a class=\"btn btn-ghost\" href=\"transactions\">Back to Transactions</a>");
            out.print("</div>");

            String sql =
                    "SELECT r.report_id, r.txn_id, r.listing_id, "
                  + "r.reason, r.description, r.evidence_file_name, "
                  + "r.status, r.resolution_note, r.created_at, r.reviewed_at, "
                  + "l.title, b.name AS buyer_name, s.name AS seller_name "
                  + "FROM product_reports r "
                  + "JOIN listings l ON l.listing_id = r.listing_id "
                  + "JOIN students b ON b.student_id = r.buyer_id "
                  + "JOIN students s ON s.student_id = r.seller_id "
                  + "WHERE r.buyer_id = ? OR r.seller_id = ? "
                  + "ORDER BY r.created_at DESC";

            boolean found = false;

            try (PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setInt(1, me);
                ps.setInt(2, me);

                try (ResultSet rs = ps.executeQuery()) {

                    while (rs.next()) {
                        found = true;

                        out.print("<article class=\"report-card glass\">");

                        out.print("<div style=\"display:flex;justify-content:space-between;gap:12px;align-items:flex-start;flex-wrap:wrap;\">");
                        out.print("<div>");
                        out.print("<h2 style=\"margin:0 0 .4rem;\">Report #"
                                + rs.getInt("report_id")
                                + " &middot; "
                                + Page.esc(rs.getString("title"))
                                + "</h2>");
                        out.print("<p class=\"muted\" style=\"margin:.2rem 0;\">Transaction #"
                                + rs.getInt("txn_id")
                                + " &middot; Buyer: "
                                + Page.esc(rs.getString("buyer_name"))
                                + " &middot; Seller: "
                                + Page.esc(rs.getString("seller_name"))
                                + "</p>");
                        out.print("</div>");

                        out.print("<span class=\"chip\">"
                                + Page.esc(rs.getString("status"))
                                + "</span>");
                        out.print("</div>");

                        out.print("<p><strong>Reason:</strong> "
                                + Page.esc(rs.getString("reason"))
                                + "</p>");

                        out.print("<p class=\"muted\">"
                                + Page.esc(rs.getString("description"))
                                + "</p>");

                        String evidence =
                                rs.getString("evidence_file_name");

                        if (evidence != null
                                && !evidence.trim().isEmpty()) {
                            out.print("<a class=\"btn btn-ghost btn-sm\" target=\"_blank\" "
                                    + "href=\"report-evidence?id="
                                    + rs.getInt("report_id")
                                    + "\">View Evidence</a>");
                        }

                        String resolution =
                                rs.getString("resolution_note");

                        if (resolution != null
                                && !resolution.trim().isEmpty()) {
                            out.print("<div class=\"notice\" style=\"margin-top:14px;\">"
                                    + "<strong>Resolution note</strong><br>"
                                    + Page.esc(resolution)
                                    + "</div>");
                        }

                        out.print("</article>");
                    }
                }
            }

            if (!found) {
                out.print("<div class=\"empty glass\">"
                        + "<h3>No reports yet</h3>"
                        + "<p class=\"muted\">If a purchased item has a problem, use Report Problem from your transaction history.</p>"
                        + "</div>");
            }

            out.print("</section>");
            out.print(Page.footer());

        } catch (Exception e) {
            e.printStackTrace();

            out.print(Page.message(
                    request,
                    "Reports",
                    "Could not load reports",
                    "Please make sure MySQL is running and the product_reports table exists.",
                    "transactions",
                    "Back to transactions",
                    "error"
            ));
        }
    }
}
