import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AISearchEngine {

    private AISearchEngine() {
        // Utility class
    }

    /**
     * Represents the user's interpreted search request.
     */
    public static class SearchIntent {

        private final String originalQuery;
        private final String category;
        private final Double maxPrice;
        private final Set<String> terms;
        private final boolean greeting;
        private final boolean helpRequest;

        public SearchIntent(
                String originalQuery,
                String category,
                Double maxPrice,
                Set<String> terms,
                boolean greeting,
                boolean helpRequest) {

            this.originalQuery = originalQuery;
            this.category = category;
            this.maxPrice = maxPrice;
            this.terms = terms;
            this.greeting = greeting;
            this.helpRequest = helpRequest;
        }

        public String getOriginalQuery() {
            return originalQuery;
        }

        public String getCategory() {
            return category;
        }

        public Double getMaxPrice() {
            return maxPrice;
        }

        public Set<String> getTerms() {
            return terms;
        }

        public boolean isGreeting() {
            return greeting;
        }

        public boolean isHelpRequest() {
            return helpRequest;
        }
    }

    // ---------------------------------------------------------
    // CATEGORY VOCABULARY
    // ---------------------------------------------------------

    private static final Map<String, String> CATEGORY_WORDS =
            new HashMap<String, String>();

    // ---------------------------------------------------------
    // SYNONYMS
    // ---------------------------------------------------------

    private static final Map<String, String[]> SYNONYMS =
            new HashMap<String, String[]>();

    // ---------------------------------------------------------
    // STOP WORDS
    // ---------------------------------------------------------

    private static final Set<String> STOP_WORDS =
            new HashSet<String>(Arrays.asList(
                    "a",
                    "an",
                    "the",
                    "i",
                    "me",
                    "my",
                    "want",
                    "need",
                    "looking",
                    "for",
                    "find",
                    "show",
                    "give",
                    "get",
                    "some",
                    "something",
                    "please",
                    "can",
                    "you",
                    "do",
                    "have",
                    "is",
                    "are",
                    "to",
                    "of",
                    "and",
                    "or",
                    "with",
                    "from",
                    "on",
                    "in",
                    "at",
                    "under",
                    "below",
                    "upto",
                    "up",
                    "within",
                    "less",
                    "than",
                    "budget",
                    "price",
                    "cost",
                    "rupees",
                    "rs",
                    "₹"
            ));

    static {
        // Books
        CATEGORY_WORDS.put("book", "Books");
        CATEGORY_WORDS.put("books", "Books");
        CATEGORY_WORDS.put("textbook", "Books");
        CATEGORY_WORDS.put("textbooks", "Books");
        CATEGORY_WORDS.put("novel", "Books");
        CATEGORY_WORDS.put("novels", "Books");
        CATEGORY_WORDS.put("programming", "Books");
        CATEGORY_WORDS.put("coding", "Books");
        CATEGORY_WORDS.put("study", "Books");
        CATEGORY_WORDS.put("academic", "Books");

        // Electronics
        CATEGORY_WORDS.put("laptop", "Electronics");
        CATEGORY_WORDS.put("laptops", "Electronics");
        CATEGORY_WORDS.put("computer", "Electronics");
        CATEGORY_WORDS.put("computers", "Electronics");
        CATEGORY_WORDS.put("phone", "Electronics");
        CATEGORY_WORDS.put("mobile", "Electronics");
        CATEGORY_WORDS.put("smartphone", "Electronics");
        CATEGORY_WORDS.put("tablet", "Electronics");
        CATEGORY_WORDS.put("headphone", "Electronics");
        CATEGORY_WORDS.put("headphones", "Electronics");
        CATEGORY_WORDS.put("earphone", "Electronics");
        CATEGORY_WORDS.put("earphones", "Electronics");
        CATEGORY_WORDS.put("earbuds", "Electronics");
        CATEGORY_WORDS.put("charger", "Electronics");
        CATEGORY_WORDS.put("keyboard", "Electronics");
        CATEGORY_WORDS.put("mouse", "Electronics");
        CATEGORY_WORDS.put("monitor", "Electronics");
        CATEGORY_WORDS.put("calculator", "Electronics");
        CATEGORY_WORDS.put("camera", "Electronics");
        CATEGORY_WORDS.put("electronics", "Electronics");

        // Hostel Essentials
        CATEGORY_WORDS.put("hostel", "Hostel Essentials");
        CATEGORY_WORDS.put("room", "Hostel Essentials");
        CATEGORY_WORDS.put("mattress", "Hostel Essentials");
        CATEGORY_WORDS.put("pillow", "Hostel Essentials");
        CATEGORY_WORDS.put("bucket", "Hostel Essentials");
        CATEGORY_WORDS.put("lamp", "Hostel Essentials");
        CATEGORY_WORDS.put("bag", "Hostel Essentials");
        CATEGORY_WORDS.put("essentials", "Hostel Essentials");

        // Sports
        CATEGORY_WORDS.put("cycle", "Sports");
        CATEGORY_WORDS.put("bicycle", "Sports");
        CATEGORY_WORDS.put("cycling", "Sports");
        CATEGORY_WORDS.put("cricket", "Sports");
        CATEGORY_WORDS.put("football", "Sports");
        CATEGORY_WORDS.put("basketball", "Sports");
        CATEGORY_WORDS.put("badminton", "Sports");
        CATEGORY_WORDS.put("sports", "Sports");

        // Accessories
        CATEGORY_WORDS.put("watch", "Accessories");
        CATEGORY_WORDS.put("wallet", "Accessories");
        CATEGORY_WORDS.put("sunglasses", "Accessories");
        CATEGORY_WORDS.put("belt", "Accessories");
        CATEGORY_WORDS.put("accessory", "Accessories");
        CATEGORY_WORDS.put("accessories", "Accessories");

        // Other
        CATEGORY_WORDS.put("other", "Other");

        // Synonyms
        SYNONYMS.put("laptop",
                new String[]{"notebook", "computer", "pc"});

        SYNONYMS.put("programming",
                new String[]{"coding", "java", "python", "software"});

        SYNONYMS.put("book",
                new String[]{"textbook", "textbooks", "novel", "study"});

        SYNONYMS.put("phone",
                new String[]{"mobile", "smartphone"});

        SYNONYMS.put("headphones",
                new String[]{"headphone", "earphones", "earbuds"});

        SYNONYMS.put("calculator",
                new String[]{"scientific", "casio"});

        SYNONYMS.put("cycle",
                new String[]{"bicycle", "bike", "cycling"});

        SYNONYMS.put("hostel",
                new String[]{"room", "dorm", "essentials"});

        SYNONYMS.put("electronics",
                new String[]{"electronic", "device", "gadgets"});

        SYNONYMS.put("cheap",
                new String[]{"budget", "affordable", "lowcost"});

        SYNONYMS.put("coding",
                new String[]{"programming", "java", "python"});
    }

    // ---------------------------------------------------------
    // MAIN QUERY UNDERSTANDING
    // ---------------------------------------------------------

    public static SearchIntent understand(String query) {

        String original = query == null ? "" : query.trim();

        String normalized = normalize(original);

        boolean greeting = isGreeting(normalized);
        boolean help = isHelpRequest(normalized);

        String category = detectCategory(normalized);

        Double maxPrice = detectMaxPrice(normalized);

        Set<String> terms = new LinkedHashSet<String>();

        if (!greeting && !help) {
            terms.addAll(extractTerms(normalized));

            // Expand important concepts.
            terms.addAll(expandTerms(terms));

            // Add category-related search terms.
            if (category != null) {
                if ("Books".equals(category)) {
                    terms.add("book");
                    terms.add("books");
                } else if ("Electronics".equals(category)) {
                    terms.add("electronics");
                } else if ("Hostel Essentials".equals(category)) {
                    terms.add("hostel");
                } else if ("Sports".equals(category)) {
                    terms.add("sports");
                } else if ("Accessories".equals(category)) {
                    terms.add("accessories");
                }
            }
        }

        return new SearchIntent(
                original,
                category,
                maxPrice,
                terms,
                greeting,
                help
        );
    }

    // ---------------------------------------------------------
    // NORMALIZATION
    // ---------------------------------------------------------

    private static String normalize(String text) {

        if (text == null) {
            return "";
        }

        String value = text.toLowerCase();

        value = value.replace("₹", " ");
        value = value.replaceAll("[^a-z0-9. ]", " ");
        value = value.replaceAll("\\s+", " ");

        return value.trim();
    }

    // ---------------------------------------------------------
    // GREETING DETECTION
    // ---------------------------------------------------------

    private static boolean isGreeting(String text) {

        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String value = text.trim();

        return value.equals("hi")
                || value.equals("hello")
                || value.equals("hey")
                || value.equals("hai")
                || value.equals("hii")
                || value.equals("hiii")
                || value.equals("good morning")
                || value.equals("good afternoon")
                || value.equals("good evening")
                || value.equals("good night");
    }

    // ---------------------------------------------------------
    // HELP DETECTION
    // ---------------------------------------------------------

    private static boolean isHelpRequest(String text) {

        if (text == null || text.isEmpty()) {
            return false;
        }

        return text.contains("how do i sell")
                || text.contains("how to sell")
                || text.contains("how can i sell")
                || text.contains("list an item")
                || text.contains("create listing")
                || text.contains("how do i add")
                || text.contains("how to add")
                || text.contains("how do i buy")
                || text.contains("how to buy")
                || text.contains("how does cart")
                || text.contains("how to use cart")
                || text.contains("how does checkout")
                || text.contains("how to checkout");
    }

    // ---------------------------------------------------------
    // CATEGORY DETECTION
    // ---------------------------------------------------------

    private static String detectCategory(String text) {

        if (text == null || text.isEmpty()) {
            return null;
        }

        String[] words = text.split("\\s+");

        for (String word : words) {

            String category = CATEGORY_WORDS.get(word);

            if (category != null) {
                return category;
            }
        }

        // Multi-word concepts
        if (text.contains("hostel essentials")
                || text.contains("hostel item")
                || text.contains("hostel items")) {
            return "Hostel Essentials";
        }

        return null;
    }

    // ---------------------------------------------------------
    // PRICE DETECTION
    // ---------------------------------------------------------

    private static Double detectMaxPrice(String text) {

        if (text == null || text.isEmpty()) {
            return null;
        }

        String normalized = text.toLowerCase();

        /*
         * Examples supported:
         *
         * laptop under 30000
         * laptop below 30000
         * laptop upto 30000
         * laptop up to 30000
         * laptop within 30000
         * laptop less than 30000
         * laptop budget 30000
         * laptop price 30000
         */

        String[] patterns = new String[]{
                "under\\s+(\\d+(?:\\.\\d+)?)",
                "below\\s+(\\d+(?:\\.\\d+)?)",
                "upto\\s+(\\d+(?:\\.\\d+)?)",
                "up\\s+to\\s+(\\d+(?:\\.\\d+)?)",
                "within\\s+(\\d+(?:\\.\\d+)?)",
                "less\\s+than\\s+(\\d+(?:\\.\\d+)?)",
                "budget\\s+(?:of\\s+)?(\\d+(?:\\.\\d+)?)",
                "price\\s+(?:of\\s+)?(\\d+(?:\\.\\d+)?)"
        };

        for (String pattern : patterns) {

            java.util.regex.Pattern p =
                    java.util.regex.Pattern.compile(pattern);

            java.util.regex.Matcher m =
                    p.matcher(normalized);

            if (m.find()) {
                try {
                    return Double.valueOf(m.group(1));
                } catch (NumberFormatException ignored) {
                    // Continue searching.
                }
            }
        }

        return null;
    }

    // ---------------------------------------------------------
    // TERM EXTRACTION
    // ---------------------------------------------------------

    private static Set<String> extractTerms(String text) {

        Set<String> terms = new LinkedHashSet<String>();

        if (text == null || text.isEmpty()) {
            return terms;
        }

        String[] words = text.split("\\s+");

        for (String word : words) {

            if (word == null || word.isEmpty()) {
                continue;
            }

            if (STOP_WORDS.contains(word)) {
                continue;
            }

            // Ignore standalone numbers.
            if (word.matches("\\d+(?:\\.\\d+)?")) {
                continue;
            }

            if (word.length() >= 2) {
                terms.add(word);
            }
        }

        // Preserve useful multi-word concepts.
        if (text.contains("programming book")) {
            terms.add("programming");
            terms.add("book");
        }

        if (text.contains("java book")) {
            terms.add("java");
            terms.add("book");
        }

        if (text.contains("cheap laptop")) {
            terms.add("cheap");
            terms.add("laptop");
        }

        if (text.contains("gaming laptop")) {
            terms.add("gaming");
            terms.add("laptop");
        }

        if (text.contains("hostel item")) {
            terms.add("hostel");
            terms.add("item");
        }

        return terms;
    }

    // ---------------------------------------------------------
    // SYNONYM EXPANSION
    // ---------------------------------------------------------

    private static Set<String> expandTerms(Set<String> originalTerms) {

        Set<String> expanded =
                new LinkedHashSet<String>(originalTerms);

        for (String term : originalTerms) {

            String[] synonyms = SYNONYMS.get(term);

            if (synonyms != null) {
                expanded.addAll(Arrays.asList(synonyms));
            }
        }

        return expanded;
    }

    // ---------------------------------------------------------
    // SEARCH SCORING
    // ---------------------------------------------------------

    public static double calculateRelevance(
            SearchIntent intent,
            String title,
            String category,
            String description,
            String keywords,
            String condition,
            double price) {

        if (intent == null) {
            return 0;
        }

        if (intent.isGreeting() || intent.isHelpRequest()) {
            return 0;
        }

        String safeTitle = normalize(title);
        String safeCategory = normalize(category);
        String safeDescription = normalize(description);
        String safeKeywords = normalize(keywords);
        String safeCondition = normalize(condition);

        double score = 0;

        String requestedCategory = intent.getCategory();

        // -----------------------------------------------------
        // CATEGORY MATCH
        // -----------------------------------------------------

        if (requestedCategory != null) {

            String wanted = normalize(requestedCategory);

            if (safeCategory.equals(wanted)) {
                score += 30;
            } else {
                score -= 30;
            }
        }

        // -----------------------------------------------------
        // TERM MATCHING
        // -----------------------------------------------------

        int matchedTerms = 0;

        for (String term : intent.getTerms()) {

            String searchTerm = normalize(term);

            if (searchTerm.isEmpty()) {
                continue;
            }

            boolean titleMatch =
                    containsWordOrPhrase(safeTitle, searchTerm);

            boolean keywordMatch =
                    containsWordOrPhrase(safeKeywords, searchTerm);

            boolean categoryMatch =
                    containsWordOrPhrase(safeCategory, searchTerm);

            boolean descriptionMatch =
                    containsWordOrPhrase(safeDescription, searchTerm);

            boolean conditionMatch =
                    containsWordOrPhrase(safeCondition, searchTerm);

            boolean matched =
                    titleMatch
                            || keywordMatch
                            || categoryMatch
                            || descriptionMatch
                            || conditionMatch;

            if (!matched) {
                continue;
            }

            matchedTerms++;

            if (titleMatch) {
                score += 14;
            }

            if (keywordMatch) {
                score += 10;
            }

            if (categoryMatch) {
                score += 8;
            }

            if (descriptionMatch) {
                score += 5;
            }

            if (conditionMatch) {
                score += 2;
            }
        }

        // -----------------------------------------------------
        // EXACT TITLE PHRASE
        // -----------------------------------------------------

        String originalQuery = normalize(intent.getOriginalQuery());

        if (!originalQuery.isEmpty()
                && safeTitle.contains(originalQuery)) {

            score += 20;
        }

        // -----------------------------------------------------
        // PRICE / BUDGET
        // -----------------------------------------------------

        Double maxPrice = intent.getMaxPrice();

        if (maxPrice != null) {

            if (price <= maxPrice.doubleValue()) {

                // Products comfortably inside the budget receive
                // a small additional relevance boost.
                double difference =
                        maxPrice.doubleValue() - price;

                if (difference >= maxPrice.doubleValue() * 0.50) {
                    score += 12;
                } else if (difference >= maxPrice.doubleValue() * 0.20) {
                    score += 8;
                } else {
                    score += 3;
                }

            } else {

                // Products over the requested budget are heavily
                // penalized. The servlet also filters them out.
                score -= 50;
            }
        }

        // -----------------------------------------------------
        // EXTRA MATCH BONUS
        // -----------------------------------------------------

        if (matchedTerms >= 3) {
            score += 10;
        } else if (matchedTerms == 2) {
            score += 6;
        } else if (matchedTerms == 1) {
            score += 2;
        }

        /*
         * If the user typed a real search query and absolutely
         * nothing matched, return zero rather than showing
         * unrelated products.
         */
        if (intent.getTerms() != null
                && !intent.getTerms().isEmpty()
                && matchedTerms == 0
                && requestedCategory == null
                && maxPrice == null) {

            return 0;
        }

        return normalizeScoreDouble(score);
    }

    // ---------------------------------------------------------
    // WORD / PHRASE MATCH
    // ---------------------------------------------------------

    private static boolean containsWordOrPhrase(
            String text,
            String term) {

        if (text == null || term == null) {
            return false;
        }

        if (text.isEmpty() || term.isEmpty()) {
            return false;
        }

        // Exact phrase first.
        if (text.contains(term)) {
            return true;
        }

        return false;
    }

    // ---------------------------------------------------------
    // SCORE NORMALIZATION
    // ---------------------------------------------------------

    public static int normalizeScore(double score) {

        if (score < 0) {
            return 0;
        }

        if (score > 100) {
            return 100;
        }

        return (int) Math.round(score);
    }

    private static double normalizeScoreDouble(double score) {

        if (score < 0) {
            return 0;
        }

        if (score > 100) {
            return 100;
        }

        return score;
    }

    // ---------------------------------------------------------
    // SEARCH TERMS ACCESS
    // ---------------------------------------------------------

    public static Set<String> getSearchTerms(String query) {

        SearchIntent intent = understand(query);

        if (intent == null || intent.getTerms() == null) {
            return Collections.emptySet();
        }

        return intent.getTerms();
    }

    // ---------------------------------------------------------
    // SIMPLE CATEGORY HELPER
    // ---------------------------------------------------------

    public static String detectCategoryForQuery(String query) {
        return understand(query).getCategory();
    }

    // ---------------------------------------------------------
    // SIMPLE PRICE HELPER
    // ---------------------------------------------------------

    public static Double detectPriceForQuery(String query) {
        return understand(query).getMaxPrice();
    }
}