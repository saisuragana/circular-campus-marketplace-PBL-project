import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.*;

@WebServlet("/report-evidence")
public class ReportEvidenceServlet extends HttpServlet {

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
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED
            );
            return;
        }

        String idParam =
                request.getParameter("id");

        if (idParam == null) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST
            );
            return;
        }

        int reportId;

        try {
            reportId =
                    Integer.parseInt(idParam);
        } catch (NumberFormatException e) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST
            );
            return;
        }

        try (Connection con =
                     DBConnection.getConnection()) {

            String sql =
                    "SELECT evidence_file_name "
                  + "FROM product_reports "
                  + "WHERE report_id = ? "
                  + "AND (buyer_id = ? OR seller_id = ?)";

            String fileName = null;

            try (PreparedStatement ps =
                         con.prepareStatement(sql)) {

                ps.setInt(1, reportId);
                ps.setInt(2, me);
                ps.setInt(3, me);

                try (ResultSet rs =
                             ps.executeQuery()) {

                    if (!rs.next()) {
                        response.sendError(
                                HttpServletResponse.SC_NOT_FOUND
                        );
                        return;
                    }

                    fileName =
                            rs.getString(
                                    "evidence_file_name"
                            );
                }
            }

            if (fileName == null
                    || fileName.trim().isEmpty()) {

                response.sendError(
                        HttpServletResponse.SC_NOT_FOUND
                );

                return;
            }

            /*
             * Only accept our generated report filename.
             */
            if (!fileName.matches(
                    "report_[a-fA-F0-9\\-]+\\.(jpg|png|webp)"
            )) {

                response.sendError(
                        HttpServletResponse.SC_BAD_REQUEST
                );

                return;
            }

            File base =
                    new File(REPORT_DIR)
                            .getCanonicalFile();

            File file =
                    new File(
                            base,
                            fileName
                    ).getCanonicalFile();

            if (!file.toPath()
                    .startsWith(base.toPath())
                    || !file.exists()
                    || !file.isFile()) {

                response.sendError(
                        HttpServletResponse.SC_NOT_FOUND
                );

                return;
            }

            String contentType =
                    getServletContext()
                            .getMimeType(
                                    file.getName()
                            );

            if (contentType == null) {
                contentType =
                        "application/octet-stream";
            }

            response.setContentType(contentType);

            response.setHeader(
                    "Cache-Control",
                    "private, max-age=3600"
            );

            response.setContentLengthLong(
                    file.length()
            );

            try (InputStream in =
                         new java.io.FileInputStream(file);
                 OutputStream out =
                         response.getOutputStream()) {

                byte[] buffer =
                        new byte[8192];

                int len;

                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }

        } catch (SQLException e) {

            e.printStackTrace();

            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            );
        }
    }
}