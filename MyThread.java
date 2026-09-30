import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

public class MyThread implements Runnable {
    private final int id;
    private static final ObjectMapper mapper = new ObjectMapper();

    public static List<Article> allArticles = Collections.synchronizedList(new ArrayList<>());
    public static List<Article> finalArticles = Collections.synchronizedList(new ArrayList<>());

    public static ConcurrentHashMap<String, Integer> uuidCounts = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Integer> titleCounts = new ConcurrentHashMap<>();

    public static ConcurrentHashMap<String, List<String>> CategoriesMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, List<String>> LanguageMap = new ConcurrentHashMap<>();

    public static List<List<Article>> sortedListsArticle = Collections.synchronizedList(new ArrayList<>());
    public static Article globalMostRecentArticle = null;
    public static List<List<Map.Entry<String, Integer>>> sortedListsKeyword = Collections.synchronizedList(new ArrayList<>());

    public static ConcurrentHashMap<String, Integer> keywordCounts = new ConcurrentHashMap<>();

    public static ConcurrentHashMap<String, Integer> authorCounts = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Integer> languageCounts = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Integer> categoryCounts = new ConcurrentHashMap<>();

    public MyThread(int id) {
        this.id = id;
    }

    @Override
    public void run() {
        parseArticles();
        try { Tema1.barrier.await(); } catch (Exception e) { e.printStackTrace(); }

        removeDuplicates();
        try { Tema1.barrier.await(); } catch (Exception e) { e.printStackTrace(); }

        organizeArticlesIntoCategories();
        try { Tema1.barrier.await(); } catch (Exception e) { e.printStackTrace(); }

        writeCategoryFiles();
        try { Tema1.barrier.await(); } catch (Exception e) { e.printStackTrace(); }

        organizeArticlesIntoLanguages();
        try { Tema1.barrier.await(); } catch (Exception e) { e.printStackTrace(); }

        writeLanguageFiles();
        try { Tema1.barrier.await(); } catch (Exception e) { e.printStackTrace(); }

        writeGlobalSortedArticles();
        try { Tema1.barrier.await(); } catch (Exception e) { e.printStackTrace(); }

        processKeywords();
        try { Tema1.barrier.await(); } catch (Exception e) { e.printStackTrace(); }

        writeKeywordsFile();
        try{ Tema1.barrier.await(); } catch (Exception e) { e.printStackTrace(); }

        processReports();
        try{ Tema1.barrier.await(); } catch (Exception e) { e.printStackTrace(); }

        writeReports();
        try{ Tema1.barrier.await(); } catch (Exception e) { e.printStackTrace(); }
    }

    private void parseArticles() {
        int N = Tema1.files.size();
        int start = (id * N) / Tema1.P;
        int end = Math.min((id + 1) * N / Tema1.P, N);

        for (int i = start; i < end; i++) {
            String file = Tema1.files.get(i);
            List<Article> localArticles = parseJson(new File(file));
            allArticles.addAll(localArticles);
        }
    }

    private List<Article> parseJson(File jsonFile) {
        List<Article> articles = new ArrayList<>();
        if (!jsonFile.exists() || jsonFile.length() == 0) return articles;

        try {
            JsonNode arr = mapper.readTree(jsonFile);
            if (arr == null || !arr.isArray()) {
                return articles;
            }

            for (JsonNode node : arr) {
                if (node == null) continue;

                Article a = new Article();
                a.uuid = node.path("uuid").asText("");
                a.title = node.path("title").asText("");
                a.author = node.path("author").asText("");
                a.url = node.path("url").asText("");
                a.text = node.path("text").asText("");
                a.published = node.path("published").asText("");
                a.language = node.path("language").asText("");

                JsonNode cats = node.path("categories");
                if (cats != null && cats.isArray()) {
                    for (JsonNode cat : cats) {
                        if (cat != null) {
                            a.categories.add(cat.asText(""));
                        }
                    }
                }
                articles.add(a);
            }
        } catch (Exception e) {
        }
        return articles;
    }

    private void removeDuplicates() {
        int N = allArticles.size();
        int start = (id * N) / Tema1.P;
        int end = Math.min((id + 1) * N / Tema1.P, N);

        for (int i = start; i < end; i++) {
            Article article = allArticles.get(i);
            if(article.uuid != null && !article.uuid.isEmpty()) uuidCounts.merge(article.uuid, 1, Integer::sum);
            if(article.title != null && !article.title.isEmpty()) titleCounts.merge(article.title, 1, Integer::sum);
        }

        try { Tema1.barrier.await(); } catch (Exception e) { e.printStackTrace(); }

        List<Article> localFinalArticles = new ArrayList<>();
        for (int i = start; i < end; i++) {
            Article article = allArticles.get(i);
            boolean isUnique = true;
            if (article.uuid != null && uuidCounts.get(article.uuid) > 1) isUnique = false;
            if (article.title != null && titleCounts.get(article.title) > 1) isUnique = false;

            if (isUnique) {
                localFinalArticles.add(article);
            }
        }
        finalArticles.addAll(localFinalArticles);
    }

    private void organizeArticlesIntoCategories() {
        int N = finalArticles.size();
        int start = (id * N) / Tema1.P;
        int end = Math.min((id + 1) * N / Tema1.P, N);

        for (int i = start; i < end; i++) {
            Article article = finalArticles.get(i);
            if (article.categories != null) {
                Set<String> uniqueCats = new HashSet<>(article.categories);
                for (String category : uniqueCats) {
                    if (Tema1.categories.contains(category)) {
                        CategoriesMap
                                .computeIfAbsent(category, k -> Collections.synchronizedList(new ArrayList<>()))
                                .add(article.uuid);
                    }
                }
            }
        }
    }

    private void writeCategoryFiles() {
        List<String> categorisFound = new ArrayList<>(CategoriesMap.keySet());
        int N = categorisFound.size();
        int start = (id * N) / Tema1.P;
        int end = Math.min((id + 1) * N / Tema1.P, N);

        for (int i = start; i < end; i++) {
            String categoryName = categorisFound.get(i);
            List<String> uuids = CategoriesMap.get(categoryName);
            Collections.sort(uuids);

            String filename = categoryName.replace(",", "").replaceAll("\\s+", "_") + ".txt";
            try (PrintWriter pw = new PrintWriter(filename)) {
                for (String uid : uuids) pw.println(uid);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void organizeArticlesIntoLanguages() {
        int N = finalArticles.size();
        int start = (id * N) / Tema1.P;
        int end = Math.min((id + 1) * N / Tema1.P, N);

        for (int i = start; i < end; i++) {
            Article article = finalArticles.get(i);
            String language = article.language;
            if (language != null && Tema1.languages.contains(language)) {
                LanguageMap.computeIfAbsent(language, k -> Collections.synchronizedList(new ArrayList<>())).add(article.uuid);
            }
        }
    }

    private void writeLanguageFiles() {
        List<String> languagesFound = new ArrayList<>(LanguageMap.keySet());
        int N = languagesFound.size();
        int start = (id * N) / Tema1.P;
        int end = Math.min((id + 1) * N / Tema1.P, N);

        for (int i = start; i < end; i++) {
            String languageName = languagesFound.get(i);
            List<String> uuids = LanguageMap.get(languageName);
            Collections.sort(uuids);

            String filename = languageName + ".txt";
            try (PrintWriter pw = new PrintWriter(filename)) {
                for (String uid : uuids) pw.println(uid);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void writeGlobalSortedArticles() {
        Comparator<Article> comp = (a1, a2) -> {
            String p1 = a1.published != null ? a1.published : "";
            String p2 = a2.published != null ? a2.published : "";
            int res = p2.compareTo(p1);
            if (res == 0) {
                String u1 = a1.uuid != null ? a1.uuid : "";
                String u2 = a2.uuid != null ? a2.uuid : "";
                return u1.compareTo(u2);
            }
            return res;
        };

        int N = finalArticles.size();
        int start = (id * N) / Tema1.P;
        int end = Math.min((id + 1) * N / Tema1.P, N);

        List<Article> localSorted = new ArrayList<>(finalArticles.subList(start, end));
        localSorted.sort(comp);
        Collections.reverse(localSorted);

        if (!localSorted.isEmpty()) {
            sortedListsArticle.add(localSorted);
        }

        try { Tema1.barrier.await(); } catch (Exception e) { e.printStackTrace(); }

        if (id == 0) {
            try (PrintWriter pw = new PrintWriter("all_articles.txt")) {
                while (!sortedListsArticle.isEmpty()) {
                    int bestListIndex = -1;
                    Article bestArticle = null;

                    for (int i = 0; i < sortedListsArticle.size(); i++) {
                        List<Article> currentList = sortedListsArticle.get(i);
                        Article tail = currentList.get(currentList.size() - 1);
                        if (bestArticle == null || comp.compare(tail, bestArticle) < 0) {
                            bestArticle = tail;
                            bestListIndex = i;
                        }
                    }

                    if (bestListIndex != -1) {
                        if (globalMostRecentArticle == null) {
                            globalMostRecentArticle = bestArticle;
                        }
                        pw.println(bestArticle.uuid + " " + bestArticle.published);
                        sortedListsArticle.get(bestListIndex).remove(sortedListsArticle.get(bestListIndex).size() - 1);
                        if (sortedListsArticle.get(bestListIndex).isEmpty()) {
                            sortedListsArticle.remove(bestListIndex);
                        }
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void processKeywords() {
        int N = finalArticles.size();
        int start = (id * N) / Tema1.P;
        int end = Math.min((id + 1) * N / Tema1.P, N);

        for (int i = start; i < end; i++) {
            Article article = finalArticles.get(i);
            if ("english".equals(article.language) && article.text != null) {
                String textLower = article.text.toLowerCase();
                String[] words = textLower.split("\\s+");
                Set<String> uniqueWordsInArticle = new HashSet<>();

                for (String word : words) {
                    String cleanWord = word.replaceAll("[^a-z]", "");
                    if (!cleanWord.isEmpty() && !Tema1.linkingWords.contains(cleanWord)) {
                        uniqueWordsInArticle.add(cleanWord);
                    }
                }

                for (String word : uniqueWordsInArticle) {
                    keywordCounts.merge(word, 1, Integer::sum);
                }
            }
        }
    }

    private void writeKeywordsFile() {
        Comparator<Map.Entry<String, Integer>> comp = (e1, e2) -> {
            int countCompare = e2.getValue().compareTo(e1.getValue());
            if (countCompare != 0) {
                return countCompare;
            }
            return e1.getKey().compareTo(e2.getKey());
        };

        List<Map.Entry<String, Integer>> localSorted = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : keywordCounts.entrySet()) {
            if (Math.abs(entry.getKey().hashCode() % Tema1.P) == id) {
                localSorted.add(entry);
            }
        }

        localSorted.sort(comp);
        Collections.reverse(localSorted);

        if (!localSorted.isEmpty()) {
            sortedListsKeyword.add(localSorted);
        }

        try { Tema1.barrier.await(); } catch (Exception e) { e.printStackTrace(); }

        if (id == 0) {
            try (PrintWriter pw = new PrintWriter("keywords_count.txt")) {
                while (!sortedListsKeyword.isEmpty()) {
                    int bestListIndex = -1;
                    Map.Entry<String, Integer> bestEntry = null;

                    for (int i = 0; i < sortedListsKeyword.size(); i++) {
                        List<Map.Entry<String, Integer>> currentList = sortedListsKeyword.get(i);
                        Map.Entry<String, Integer> tail = currentList.get(currentList.size() - 1);

                        if (bestEntry == null || comp.compare(tail, bestEntry) < 0) {
                            bestEntry = tail;
                            bestListIndex = i;
                        }
                    }

                    if (bestListIndex != -1) {
                        pw.println(bestEntry.getKey() + " " + bestEntry.getValue());
                        sortedListsKeyword.get(bestListIndex).remove(sortedListsKeyword.get(bestListIndex).size() - 1);
                        if (sortedListsKeyword.get(bestListIndex).isEmpty()) {
                            sortedListsKeyword.remove(bestListIndex);
                        }
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void processReports(){

        Map<String, Integer> localAuthorCounts = new HashMap<>();
        Map<String, Integer> localLanguageCounts = new HashMap<>();
        Map<String, Integer> localCategoryCounts = new HashMap<>();

        int N = finalArticles.size();
        int start = (id * N) / Tema1.P;
        int end = Math.min((id + 1) * N / Tema1.P, N);

        for (int i = start; i < end; i++) {
            Article article = finalArticles.get(i);

            if (article.author != null && !article.author.isEmpty()) {
                localAuthorCounts.merge(article.author, 1, Integer::sum);
            }

            if (article.language != null && !article.language.isEmpty()) {
                localLanguageCounts.merge(article.language, 1, Integer::sum);
            }

            if (article.categories != null) {
                Set<String> uniqueCategories = new HashSet<>(article.categories);
                for (String category : uniqueCategories) {
                    if (Tema1.categories.contains(category)) {
                        localCategoryCounts.merge(category, 1, Integer::sum);
                    }
                }
            }
        }

        for (Map.Entry<String, Integer> entry : localAuthorCounts.entrySet()) {
            authorCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : localLanguageCounts.entrySet()) {
            languageCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : localCategoryCounts.entrySet()) {
            categoryCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }

    private void writeReports() {
        if (id == 0) {
            try (PrintWriter pw = new PrintWriter("reports.txt")) {
                int duplicates = allArticles.size() - finalArticles.size();
                pw.println("duplicates_found - " + duplicates);

                pw.println("unique_articles - " + finalArticles.size());

                Map.Entry<String, Integer> bestAuthor = getTopEntry(authorCounts);
                if (bestAuthor != null)
                    pw.println("best_author - " + bestAuthor.getKey() + " " + bestAuthor.getValue());

                Map.Entry<String, Integer> topLang = getTopEntry(languageCounts);
                if (topLang != null)
                    pw.println("top_language - " + topLang.getKey() + " " + topLang.getValue());

                Map.Entry<String, Integer> topCat = getTopEntry(categoryCounts);
                if (topCat != null) {
                    String normCat = topCat.getKey().replace(",", "").replaceAll("\\s+", "_");
                    pw.println("top_category - " + normCat + " " + topCat.getValue());
                }

                if (!finalArticles.isEmpty()) {
                    pw.println("most_recent_article - " + globalMostRecentArticle.published + " " + globalMostRecentArticle.url);
                }

                Map.Entry<String, Integer> topKey = getTopEntry(keywordCounts);
                if (topKey != null)
                    pw.println("top_keyword_en - " + topKey.getKey() + " " + topKey.getValue());

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private Map.Entry<String, Integer> getTopEntry(Map<String, Integer> map) {
        Map.Entry<String, Integer> maxEntry = null;

        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (maxEntry == null) {
                maxEntry = entry;
                continue;
            }

            int valCmp = entry.getValue().compareTo(maxEntry.getValue());

            if (valCmp > 0) {
                maxEntry = entry;
            } else if (valCmp == 0) {
                if (entry.getKey().compareTo(maxEntry.getKey()) < 0) {
                    maxEntry = entry;
                }
            }
        }
        return maxEntry;
    }
}