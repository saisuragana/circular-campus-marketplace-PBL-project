
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * GET /ai-suggest
 *
 * AI-assisted listing helper.
 *
 * Returns:
 * - suggested category
 * - suggested keywords
 * - improved title
 * - price estimate when category/condition are known
 * - listing quality score
 *
 * All intelligence is local and explainable; no external AI API is called.
 */
@WebServlet("/ai-suggest")
public class AIRecommendationServlet extends HttpServlet {

    private static final String[][] RULES = {
        {"java", "Books", "java, programming, textbook, oop"},
        {"python", "Books", "python, programming, textbook, coding"},
        {"textbook", "Books", "textbook, semester, study, notes"},
        {"book", "Books", "book, textbook, study, semester"},
        {"notes", "Books", "notes, study, semester, handwritten"},
        {"math", "Books", "maths, mathematics, textbook, engineering"},
        {"physics", "Books", "physics, textbook, first year, study"},
        {"network", "Books", "networks, cn, textbook, tcp"},

        {"laptop", "Electronics", "laptop, computer, technology, portable"},
        {"macbook", "Electronics", "laptop, apple, computer, technology"},
        {"phone", "Electronics", "phone, mobile, android, electronics"},
        {"mobile", "Electronics", "phone, mobile, android, electronics"},
        {"calculator", "Electronics", "calculator, scientific, exam, maths"},
        {"headphone", "Electronics", "headphones, audio, music, bluetooth"},
        {"earphone", "Electronics", "earphones, audio, music, bluetooth"},
        {"keyboard", "Electronics", "keyboard, mechanical, computer, typing"},
        {"mouse", "Electronics", "mouse, computer, wireless, accessory"},
        {"monitor", "Electronics", "monitor, display, computer, screen"},
        {"arduino", "Electronics", "arduino, microcontroller, project, embedded"},
        {"esp32", "Electronics", "esp32, iot, wifi, board"},
        {"raspberry", "Electronics", "raspberry pi, sbc, project, linux"},
        {"tablet", "Electronics", "tablet, stylus, drawing, digital"},
        {"charger", "Electronics", "charger, adapter, power, cable"},

        {"lamp", "Hostel Essentials", "lamp, study, led, hostel"},
        {"kettle", "Hostel Essentials", "kettle, hostel, kitchen, electric"},
        {"mattress", "Hostel Essentials", "mattress, hostel, bed, sleep"},
        {"bucket", "Hostel Essentials", "bucket, hostel, bathroom, daily"},
        {"fan", "Hostel Essentials", "fan, hostel, cooling, summer"},
        {"table", "Hostel Essentials", "table, study, hostel, desk"},
        {"chair", "Hostel Essentials", "chair, study, hostel, seating"},

        {"cycle", "Sports", "bicycle, cycle, fitness, commute"},
        {"bicycle", "Sports", "bicycle, cycle, fitness, commute"},
        {"cricket", "Sports", "cricket, bat, sports, ground"},
        {"badminton", "Sports", "badminton, racket, sports, shuttle"},
        {"football", "Sports", "football, sports, ground, ball"},
        {"dumbbell", "Sports", "dumbbell, gym, fitness, weights"},
        {"gym", "Sports", "gym, fitness, workout, training"},

        {"bag", "Accessories", "bag, backpack, college, carry"},
        {"backpack", "Accessories", "backpack, bag, laptop, college"},
        {"watch", "Accessories", "watch, wearable, accessory, time"},
        {"bottle", "Accessories", "bottle, water, steel, daily"},
        {"umbrella", "Accessories", "umbrella, monsoon, rain, daily"},

        {"drafter", "Other", "drafting, drawing, graphics, board"},
        {"drawing", "Other", "drawing, graphics, board, kit"},
        {"lab coat", "Other", "lab coat, lab, chemistry, uniform"},
        {"uniform", "Other", "uniform, college, clothing, formal"}
    };

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        String title = safe(request.getParameter("title"));
        String description = safe(request.getParameter("description"));
        String condition = safe(request.getParameter("condition"));
        String categoryInput = safe(request.getParameter("category"));

        String text =
                (title + " " + description)
                        .toLowerCase(Locale.ENGLISH);

        String category = null;

        Set<String> keywords =
                new LinkedHashSet<String>();

        for (String[] rule : RULES) {

            if (text.contains(rule[0])) {

                if (category == null) {
                    category = rule[1];
                }

                for (String k :
                        rule[2].split(",")) {

                    String kw = k.trim();

                    if (!kw.isEmpty()
                            && keywords.size() < 8) {

                        keywords.add(kw);
                    }
                }
            }
        }

        boolean matched =
                category != null;

        if (category == null
                && !categoryInput.isEmpty()) {

            category = categoryInput;
        }

        if (category == null) {
            category = "Other";
        }

        if (!matched) {

            for (String w :
                    text.split("[^a-z0-9]+")) {

                if (w.length() >= 4
                        && keywords.size() < 5) {

                    keywords.add(w);
                }
            }
        }

        String suggestedTitle =
                improveTitle(title, category);

        AIQualityEngine.QualityResult quality =
                AIQualityEngine.analyze(
                        title,
                        description,
                        join(keywords),
                        category,
                        condition,
                        1,
                        1
                );

        String priceJson =
                "{\"available\":false}";

        if (!condition.isEmpty()) {

            try (
                    Connection con =
                            DBConnection.getConnection()
            ) {

                AIPriceEngine.PriceSuggestion price =
                        AIPriceEngine.suggest(
                                con,
                                category,
                                condition
                        );

                priceJson =
                        "{\"available\":true" +
                        ",\"suggested\":" +
                        price.getSuggested() +
                        ",\"low\":" +
                        price.getLow() +
                        ",\"high\":" +
                        price.getHigh() +
                        ",\"samples\":" +
                        price.getSamples() +
                        "}";
            } catch (Exception ignored) {
                // The listing page still works if price history is unavailable.
            }
        }

        StringBuilder json =
                new StringBuilder();

        json.append("{")
            .append("\"matched\":")
            .append(matched)
            .append(",\"category\":\"")
            .append(escape(category))
            .append("\",\"keywords\":[");

        int i = 0;

        for (String k : keywords) {

            if (i++ > 0) {
                json.append(",");
            }

            json.append("\"")
                .append(escape(k))
                .append("\"");
        }

        json.append("]")
            .append(",\"suggestedTitle\":\"")
            .append(escape(suggestedTitle))
            .append("\"")
            .append(",\"qualityScore\":")
            .append(quality.getScore())
            .append(",\"qualityStatus\":\"")
            .append(escape(quality.getStatus()))
            .append("\"")
            .append(",\"qualityReasons\":[");

        for (int j = 0;
             j < quality.getReasons().size();
             j++) {

            if (j > 0) {
                json.append(",");
            }

            json.append("\"")
                .append(
                        escape(
                                quality.getReasons()
                                        .get(j)
                        )
                )
                .append("\"");
        }

        json.append("]")
            .append(",\"price\":")
            .append(priceJson)
            .append("}");

        PrintWriter out =
                response.getWriter();

        out.print(
                json.toString()
        );
    }

    private String improveTitle(
            String title,
            String category) {

        if (title == null
                || title.trim().isEmpty()) {

            return "";
        }

        String cleaned =
                title.trim()
                        .replaceAll("\\s+", " ");

        String first =
                cleaned.substring(0, 1)
                        .toUpperCase(
                                Locale.ENGLISH
                        );

        String rest =
                cleaned.length() > 1
                        ? cleaned.substring(1)
                        : "";

        return first + rest;
    }

    private String join(
            Set<String> values) {

        StringBuilder sb =
                new StringBuilder();

        for (String value : values) {

            if (sb.length() > 0) {
                sb.append(", ");
            }

            sb.append(value);
        }

        return sb.toString();
    }

    private String safe(String value) {
        return value == null
                ? ""
                : value.trim();
    }

    private String escape(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }
}
