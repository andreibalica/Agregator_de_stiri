import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class Tema1 {
    public static int P;
    public static Thread[] threads;
    public static CyclicBarrier barrier;
    public static List<String> files = new ArrayList<>();

    public static Set<String> categories = new HashSet<>();
    public static Set<String> languages = new HashSet<>();
    public static Set<String> linkingWords = new HashSet<>();

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java Tema1 <threads> <articles_file> <aux_file>");
            return;
        }

        P = Integer.parseInt(args[0]);
        threads = new Thread[P];
        barrier = new CyclicBarrier(P);

        readListOfFiles(args[1]);
        initializeConfig(args[2]);

        createThreads();
        joinThreads();
    }

    private static void createThreads() {
        for(int i = 0; i < P; i++) {
            threads[i] = new Thread(new MyThread(i));
            threads[i].start();
        }
    }

    private static void joinThreads() {
        for(int i = 0; i < P; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static void readListOfFiles(String filePath) {
        String baseDir = "";
        File f = new File(filePath);
        if (f.getParent() != null) {
            baseDir = f.getParent() + File.separator;
        }

        try {
            Scanner scanner = new Scanner(new File(filePath));
            if(scanner.hasNext()) scanner.nextLine();

            while(scanner.hasNext()){
                String line = scanner.nextLine().trim();
                if (!line.isEmpty()) {
                    files.add(baseDir + line);
                }
            }
            scanner.close();
        } catch (FileNotFoundException e) {
            System.out.println("File not found : " + filePath);
        }
    }

    private static void initializeConfig(String inputsFilePath) {
        String baseDir = "";
        File f = new File(inputsFilePath);
        if (f.getParent() != null) {
            baseDir = f.getParent() + File.separator;
        }

        try {
            Scanner scanner = new Scanner(new File(inputsFilePath));
            if (scanner.hasNext()) scanner.nextLine();

            String languagePath = scanner.hasNext() ? scanner.nextLine().trim() : "";
            String categoryPath = scanner.hasNext() ? scanner.nextLine().trim() : "";
            String linkPath = scanner.hasNext() ? scanner.nextLine().trim() : "";
            scanner.close();

            readFileIntoSet(baseDir + languagePath, languages);
            readFileIntoSet(baseDir + categoryPath, categories);
            readFileIntoSet(baseDir + linkPath, linkingWords);

        } catch (FileNotFoundException e) {
            System.out.println("Config file inputs.txt not found: " + inputsFilePath);
        }
    }

    private static void readFileIntoSet(String filePath, Set<String> targetSet) {
        try {
            Scanner scanner = new Scanner(new File(filePath));
            if (scanner.hasNext()) scanner.nextLine();

            while (scanner.hasNext()) {
                String line = scanner.nextLine().trim();
                if (!line.isEmpty()) {
                    targetSet.add(line);
                }
            }
            scanner.close();
        } catch (FileNotFoundException e) {
            System.out.println("File not found: " + filePath);
        }
    }
}