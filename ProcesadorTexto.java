import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;


public final class ProcesadorTexto {

    private record Counts(long total, long exact, long containing) {
        Counts add(Counts other) {
            return new Counts(total + other.total, exact + other.exact, containing + other.containing);
        }
    }

    private static final class Worker extends Thread {
        private final String text;
        private final int from;
        private final int to;
        private final String exactTarget;
        private final String sequence;
        private Counts result;

        Worker(String text, int from, int to, String exactTarget, String sequence) {
            this.text = text;
            this.from = from;
            this.to = to;
            this.exactTarget = exactTarget;
            this.sequence = sequence;
        }

        @Override public void run() {
            result = count(text, from, to, exactTarget, sequence);
        }
    }

    private static Counts count(String text, int from, int to, String target, String sequence) {
        long total = 0, exact = 0, containing = 0;
        int i = from;
        while (i < to) {
            int cp = text.codePointAt(i);
            if (!isLetterOrMark(cp)) { i += Character.charCount(cp); continue; }
            int start = i;
            i += Character.charCount(cp);
            while (i < to) {
                cp = text.codePointAt(i);
                if (isLetterOrMark(cp)) { i += Character.charCount(cp); continue; }
                if ((cp == '\'' || cp == '’') && i + Character.charCount(cp) < to
                        && isLetterOrMark(text.codePointAt(i + Character.charCount(cp)))) {
                    i += Character.charCount(cp);
                    continue;
                }
                break;
            }
            int length = i - start;
            total++;
            if (text.regionMatches(true, start, target, 0, length) && length == target.length()) exact++;
            if (containsIgnoreCase(text, start, length, sequence)) containing++;
        }
        return new Counts(total, exact, containing);
    }

    private static boolean isLetterOrMark(int cp) {
        int type = Character.getType(cp);
        return Character.isLetter(cp) || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK || type == Character.ENCLOSING_MARK;
    }

    private static boolean containsIgnoreCase(String text, int start, int length, String sequence) {
        int last = start + length - sequence.length();
        for (int i = start; i <= last; i++)
            if (text.regionMatches(true, i, sequence, 0, sequence.length())) return true;
        return false;
    }

    private static Counts sequential(String text, String target, String sequence) {
        return count(text, 0, text.length(), target, sequence);
    }

    private static int[] boundaries(String text, int workers) {
        int[] b = new int[workers + 1];
        b[workers] = text.length();
        int cursor = 0;
        for (int i = 1; i < workers; i++) {
            int desired = (int) ((long) text.length() * i / workers);
            int p = Math.max(cursor, desired);
            while (p < text.length() && !Character.isWhitespace(text.charAt(p))) p++;
            while (p < text.length() && Character.isWhitespace(text.charAt(p))) p++;
            b[i] = p;
            cursor = p;
        }
        return b;
    }

    private static Counts parallel(String text, String target, String sequence, int threadCount) throws InterruptedException {
        int[] b = boundaries(text, threadCount);
        Worker[] workers = new Worker[threadCount];
        for (int i = 0; i < threadCount; i++) workers[i] = new Worker(text, b[i], b[i + 1], target, sequence);
        for (Worker worker : workers) worker.start();
        Counts total = new Counts(0, 0, 0);
        for (Worker worker : workers) {
            worker.join();
            total = total.add(worker.result);
        }
        return total;
    }

    private static String repeat(String text, int times) {
        return text.repeat(times);
    }

    private static long averageNanos(String text, String target, String sequence, int threads, int repetitions) throws InterruptedException {
        long sum = 0;
        for (int i = 0; i < repetitions; i++) {
            long start = System.nanoTime();
            if (threads == 1) sequential(text, target, sequence);
            else parallel(text, target, sequence, threads);
            sum += System.nanoTime() - start;
        }
        return sum / repetitions;
    }

    public static void main(String[] args) throws Exception {
        Path file = Path.of(args.length > 0 ? args[0] : "DonQuijote.txt");
        String target = (args.length > 1 ? args[1] : "Quijote").toLowerCase(Locale.ROOT);
        String sequence = (args.length > 2 ? args[2] : "ción").toLowerCase(Locale.ROOT);
        int repetitions = args.length > 3 ? Integer.parseInt(args[3]) : 5;
        int amplification = args.length > 4 ? Integer.parseInt(args[4]) : 100;
        if (repetitions < 5 || amplification < 1) throw new IllegalArgumentException("Se requieren al menos 5 repeticiones y ampliación >= 1.");

        // File I/O and amplification happen before every timed section.
        String original = Files.readString(file, StandardCharsets.UTF_8);
        for (int factor : new int[]{1, amplification}) {
            String text = factor == 1 ? original : repeat(original, factor);
            Counts expected = sequential(text, target, sequence);
            System.out.printf("\nDocumento: %s | copias: %d | caracteres: %d%n", file.getFileName(), factor, text.length());
            System.out.printf("Resultados: palabras=%d, '%s'=%d, contiene '%s'=%d%n", expected.total, target, expected.exact, sequence, expected.containing);
            System.out.println("hilos, promedio_ms, speedup");
            long sequentialAverage = averageNanos(text, target, sequence, 1, repetitions);
            System.out.printf("1, %.3f, 1.000%n", sequentialAverage / 1_000_000.0);
            for (int threads : new int[]{2, 4}) {
                long average = averageNanos(text, target, sequence, threads, repetitions);
                Counts actual = parallel(text, target, sequence, threads);
                if (!expected.equals(actual)) throw new IllegalStateException("Los resultados no coinciden para " + threads + " hilos.");
                System.out.printf("%d, %.3f, %.3f%n", threads, average / 1_000_000.0, (double) sequentialAverage / average);
            }
        }
    }
}
