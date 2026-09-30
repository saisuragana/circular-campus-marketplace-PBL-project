import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.UUID;

@WebServlet("/report-problem")
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,
        maxFileSize = 5 * 1024 * 1024,
        maxRequestSize = 7 * 1024 * 1024
)
public class ReportProblemServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final String REPORT_DIR =
            "C:\\campus-marketplace-data\\uploads\\reports";

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        Integer me = CartUtil.studentId(request);

        if (me == null) {
            response.sendRedirect("login.html");
            return;
        }

        String txnParam = request.getParameter("txnId");

        if (txnParam == null) {
            response.sendRedirect("transactions");
            return;
        }

        int txnId;

        try {
            txnId = Integer.parseInt(txnParam);
        } catch (NumberFormatException e) {
            response.sendRedirect("transactions");
            return;
        }

        response.setContentType("text/html;charset=UTF-8");

        PrintWriter out = response.getWriter();

        try (Connection con = DBConnection.getConnection()) {

            String sql =
                    "SELECT t.txn_id, "
                  + "t.listing_id, "
                  + "t.buyer_id, "
                  + "t.seller_id, "
                  + "t.payment_status, "
                  + "l.title, "
                  + "l.`condition`, "
                  + "l.price, "
                  + "s.name AS seller_name "
                  + "FROM transactions t "
                  + "JOIN listings l "
                  + "ON l.listing_id = t.listing_id "
                  + "JOIN students s "
                  + "ON s.student_id = t.seller_id "
                  + "WHERE t.txn_id = ? "
                  + "AND t.buyer_id = ?";

            try (PreparedStatement ps =
                         con.prepareStatement(sql)) {

                ps.setInt(1, txnId);
                ps.setInt(2, me);

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {

                        out.print(
                                Page.message(
                                        request,
                                        "Report Problem",
                                        "Transaction not found",
                                        "You can only report problems for your own purchases.",
                                        "transactions",
                                        "Back to transactions",
                                        "error"
                                )
                        );

                        return;
                    }

                    out.print(
                            Page.header(
                                    request,
                                    "Report Problem",
                                    "transactions"
                            )
                    );

                    StringBuilder html =
                            new StringBuilder();

                    html.append(
                            "<section class=\"txn-page\">"
                    );

                    html.append(
                            "<div class=\"txn-block glass\">"
                    );

                    html.append(
                            "<h1>Report a Problem</h1>"
                    );

                    html.append(
                            "<p class=\"muted\">"
                            + "Tell us what was wrong with the item "
                            + "you received. Upload evidence if available."
                            + "</p>"
                    );

                    html.append(
                            "<div class=\"glass\" "
                            + "style=\"padding:1rem;margin:1rem 0;\">"
                    );

                    html.append("<strong>");

                    html.append(
                            Page.esc(
                                    rs.getString("title")
                            )
                    );

                    html.append(
                            "</strong><br>Seller: "
                    );

                    html.append(
                            Page.esc(
                                    rs.getString("seller_name")
                            )
                    );

                    html.append("</div>");

                    html.append(
                            "<form method=\"post\" "
                            + "action=\"report-problem\" "
                            + "enctype=\"multipart/form-data\">"
                    );

                    html.append(
                            "<input type=\"hidden\" "
                            + "name=\"txnId\" "
                            + "value=\""
                    );

                    html.append(txnId);

                    html.append("\">");

                    html.append(
                            "<div class=\"form-group\" "
                            + "style=\"margin-bottom:1rem;\">"
                    );

                    html.append(
                            "<label for=\"reason\">"
                            + "Problem type"
                            + "</label>"
                    );

                    html.append(
                            "<select id=\"reason\" "
                            + "name=\"reason\" required>"
                    );

                    html.append(
                            "<option value=\"\">"
                            + "Select problem"
                            + "</option>"
                    );

                    html.append(
                            "<option value=\"DAMAGED\">"
                            + "Item arrived damaged"
                            + "</option>"
                    );

                    html.append(
                            "<option value=\"TORN\">"
                            + "Book/pages are torn or damaged"
                            + "</option>"
                    );

                    html.append(
                            "<option value=\"WRONG_ITEM\">"
                            + "Wrong item received"
                            + "</option>"
                    );

                    html.append(
                            "<option value=\"NOT_AS_DESCRIBED\">"
                            + "Item does not match description"
                            + "</option>"
                    );

                    html.append(
                            "<option value=\"MISSING_PARTS\">"
                            + "Parts/accessories are missing"
                            + "</option>"
                    );

                    html.append(
                            "<option value=\"OTHER\">"
                            + "Other"
                            + "</option>"
                    );

                    html.append("</select>");
                    html.append("</div>");

                    html.append(
                            "<div class=\"form-group\" "
                            + "style=\"margin-bottom:1rem;\">"
                    );

                    html.append(
                            "<label for=\"description\">"
                            + "Description"
                            + "</label>"
                    );

                    html.append(
                            "<textarea "
                            + "id=\"description\" "
                            + "name=\"description\" "
                            + "rows=\"5\" "
                            + "maxlength=\"2000\" "
                            + "required "
                            + "placeholder=\"Explain what happened "
                            + "and how the received item differs "
                            + "from the listing...\"></textarea>"
                    );

                    html.append("</div>");

                    html.append(
                            "<div class=\"form-group\" "
                            + "style=\"margin-bottom:1rem;\">"
                    );

                    html.append(
                            "<label for=\"evidence\">"
                            + "Evidence image"
                            + "</label>"
                    );

                    html.append(
                            "<input "
                            + "id=\"evidence\" "
                            + "type=\"file\" "
                            + "name=\"evidence\" "
                            + "accept=\"image/jpeg,image/png,image/webp\">"
                    );

                    html.append(
                            "<p class=\"muted\">"
                            + "Optional. Maximum 5 MB. "
                            + "Use a clear photo of the problem."
                            + "</p>"
                    );

                    html.append("</div>");

                    html.append(
                            "<div style=\"display:flex;"
                            + "gap:.75rem;flex-wrap:wrap;"
                            + "margin-top:1rem;\">"
                    );

                    html.append(
                            "<button class=\"btn btn-primary\" "
                            + "type=\"submit\">"
                            + "Submit Report"
                            + "</button>"
                    );

                    html.append(
                            "<a class=\"btn btn-ghost\" "
                            + "href=\"transactions\">"
                            + "Cancel"
                            + "</a>"
                    );

                    html.append("</div>");

                    html.append("</form>");
                    html.append("</div>");
                    html.append("</section>");

                    out.print(
                            html.toString()
                    );

                    out.print(
                            Page.footer()
                    );
                }
            }

        } catch (SQLException e) {

            e.printStackTrace();

            out.print(
                    Page.message(
                            request,
                            "Report Problem",
                            "Unable to load transaction",
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

        Integer me = CartUtil.studentId(request);

        if (me == null) {
            response.sendRedirect("login.html");
            return;
        }

        int txnId;

        try {
            txnId =
                    Integer.parseInt(
                            request.getParameter("txnId")
                    );
        } catch (Exception e) {
            response.sendRedirect("transactions");
            return;
        }

        String reason =
                request.getParameter("reason");

        String description =
                request.getParameter("description");

        if (reason == null
                || reason.trim().isEmpty()
                || description == null
                || description.trim().isEmpty()) {

            response.sendRedirect(
                    "report-problem?txnId=" + txnId
            );

            return;
        }

        Part evidence = null;

try {
    evidence = request.getPart("evidence");
} catch (javax.servlet.ServletException e) {
    throw new IOException(
            "Unable to read uploaded evidence image.",
            e
    );
}

        String savedEvidence = null;

        try {

            if (evidence != null
                    && evidence.getSize() > 0) {

                String contentType =
                        evidence.getContentType();

                if (!isAllowedImage(contentType)) {
                    throw new IllegalArgumentException(
                            "Only JPG, PNG and WEBP evidence images are allowed."
                    );
                }

                if (evidence.getSize()
                        > 5L * 1024L * 1024L) {

                    throw new IllegalArgumentException(
                            "Evidence image is larger than 5 MB."
                    );
                }

                File directory =
                        new File(REPORT_DIR);

                if (!directory.exists()
                        && !directory.mkdirs()) {

                    throw new IOException(
                            "Could not create report upload directory."
                    );
                }

                String extension =
                        extensionFor(contentType);

                savedEvidence =
                        "report_"
                        + UUID.randomUUID()
                        + extension;

                File destination =
                        new File(
                                directory,
                                savedEvidence
                        );

                try (InputStream in =
                             evidence.getInputStream()) {

                    Files.copy(
                            in,
                            destination.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }
            }

            try (Connection con =
                         DBConnection.getConnection()) {

                String transactionSql =
                        "SELECT listing_id, seller_id "
                      + "FROM transactions "
                      + "WHERE txn_id = ? "
                      + "AND buyer_id = ? "
                      + "AND payment_status = 'SUCCESS'";

                int listingId;
                int sellerId;

                try (PreparedStatement ps =
                             con.prepareStatement(
                                     transactionSql
                             )) {

                    ps.setInt(1, txnId);
                    ps.setInt(2, me);

                    try (ResultSet rs =
                                 ps.executeQuery()) {

                        if (!rs.next()) {

                            response.sendRedirect(
                                    "transactions"
                            );

                            return;
                        }

                        listingId =
                                rs.getInt("listing_id");

                        sellerId =
                                rs.getInt("seller_id");
                    }
                }

                String duplicateSql =
                        "SELECT report_id "
                      + "FROM product_reports "
                      + "WHERE txn_id = ? "
                      + "AND buyer_id = ? "
                      + "AND status = 'OPEN'";

                try (PreparedStatement ps =
                             con.prepareStatement(
                                     duplicateSql
                             )) {

                    ps.setInt(1, txnId);
                    ps.setInt(2, me);

                    try (ResultSet rs =
                                 ps.executeQuery()) {

                        if (rs.next()) {

                            response.sendRedirect(
                                    "product-reports"
                            );

                            return;
                        }
                    }
                }

                String insertSql =
                        "INSERT INTO product_reports "
                      + "(txn_id, listing_id, buyer_id, "
                      + "seller_id, reason, description, "
                      + "evidence_file_name, status) "
                      + "VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN')";

                try (PreparedStatement ps =
                             con.prepareStatement(
                                     insertSql
                             )) {

                    ps.setInt(1, txnId);
                    ps.setInt(2, listingId);
                    ps.setInt(3, me);
                    ps.setInt(4, sellerId);
                    ps.setString(5, reason);
                    ps.setString(
                            6,
                            description.trim()
                    );

                    if (savedEvidence == null) {

                        ps.setNull(
                                7,
                                Types.VARCHAR
                        );

                    } else {

                        ps.setString(
                                7,
                                savedEvidence
                        );
                    }

                    ps.executeUpdate();
                }
            }

            request.getSession()
                    .setAttribute(
                            "flash",
                            "Problem report submitted successfully."
                    );

            response.sendRedirect(
                    "product-reports"
            );

        } catch (Exception e) {

            e.printStackTrace();

            response.setContentType(
                    "text/html;charset=UTF-8"
            );

            String message =
                    e.getMessage();

            if (message == null
                    || message.trim().isEmpty()) {

                message =
                        "Unknown error while submitting the report.";
            }

            response.getWriter().print(
                    Page.message(
                            request,
                            "Report Problem",
                            "Report could not be submitted",
                            Page.esc(message),
                            "transactions",
                            "Back to transactions",
                            "error"
                    )
            );
        }
    }

    private boolean isAllowedImage(
            String contentType) {

        return "image/jpeg".equalsIgnoreCase(contentType)
                || "image/png".equalsIgnoreCase(contentType)
                || "image/webp".equalsIgnoreCase(contentType);
    }

    private String extensionFor(
            String contentType) {

        if ("image/png".equalsIgnoreCase(contentType)) {
            return ".png";
        }

        if ("image/webp".equalsIgnoreCase(contentType)) {
            return ".webp";
        }

        return ".jpg";
    }
}