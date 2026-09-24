package co.edu.uniquindio.legajo.preprocess;

import java.util.List;

/**
 * Hand-written implementation of the original Porter (1980) stemming algorithm
 * ("An algorithm for suffix stripping", Program 14(3), pp. 130-137). Optional Porter
 * stemming is one of the components domain must implement itself — hand-written; no
 * library implements it: no Lucene, OpenNLP, CoreNLP, spaCy, NLTK, or Snowball stemmer
 * dependency is used here.
 *
 * <p>The algorithm reduces a word in up to seven ordered steps (1a, 1b, 1c, 2, 3, 4,
 * 5a, 5b), each conditioned on the "measure" {@code m} of the stem — the number of
 * vowel-consonant transitions in {@code [C](VC)^m[V]} — or on simple structural
 * predicates ({@code *v*}: stem contains a vowel; {@code *d}: stem ends in a double
 * consonant; {@code *o}: stem ends consonant-vowel-consonant, second consonant not
 * W, X, or Y). This class is package-private: {@link TextPreprocessor} is the only
 * caller, gated by the {@code preprocess.stemming} flag (default {@code false}).
 *
 * <p><b>Longest-match-wins.</b> Within a step, at most one rule ever fires: the one
 * whose suffix is the longest match for the current word. Its condition is tested
 * once; if it fails, the step does nothing more — no shorter-matching suffix in the
 * same step is tried, even if it would otherwise apply. Steps 2, 3, and 4 implement
 * this through {@link #applyLongestMatchRule(StringBuilder, List)} over a suffix
 * list sorted by descending length. Steps 1a, 1b, 1c, 5a, and 5b need no such
 * dispatch: their rules are either an unconditional if/else-if chain over
 * non-overlapping suffixes (1a), structurally mutually exclusive (1b's EED branch
 * returns unconditionally before ED/ING is even considered), or a single rule (1c,
 * 5a, 5b) — none of them can fall through from a failed condition to a shorter
 * alternative.
 *
 * <p><b>Departures from the paper's printed table.</b> This implementation follows
 * Porter's own reference implementation, which the official test vocabulary at
 * <a href="https://tartarus.org/martin/PorterStemmer/">tartarus.org/martin/PorterStemmer</a>
 * encodes, rather than the 1980 paper's table verbatim. Two rules differ from what
 * the paper prints: step 2 uses {@code bli -> ble} in place of the narrower
 * {@code abli -> able} (so {@code dumbly}, {@code horribly}, {@code forcibly} stem
 * correctly), and adds {@code logi -> log} (so {@code apology} stems to
 * {@code apolog}, matching {@code analogy} -&gt; {@code analog}). Both are
 * documented departures the reference implementation makes and the vocabulary
 * encodes; {@link PorterStemmerConformanceTest} is the proof that this class
 * reproduces it exactly (0 mismatches over 23,531 words).
 */
final class PorterStemmer {

    @FunctionalInterface
    private interface SuffixCondition {
        boolean test(StringBuilder stem, int stemLength);
    }

    /** One step-2/3/4 rule: replace {@code suffix} with {@code replacement} if {@code condition} holds. */
    private record SuffixRule(String suffix, String replacement, SuffixCondition condition) {
    }

    private static final SuffixCondition MEASURE_GT_0 = (sb, stemLength) -> measure(sb, stemLength) > 0;
    private static final SuffixCondition MEASURE_GT_1 = (sb, stemLength) -> measure(sb, stemLength) > 1;

    /** (m>1 and (*S or *T)) ION -> (delete); the only step-4 rule with an extra letter test. */
    private static final SuffixCondition STEP4_ION_CONDITION = (sb, stemLength) -> {
        if (stemLength <= 0) {
            return false;
        }
        char precedingLetter = sb.charAt(stemLength - 1);
        return (precedingLetter == 's' || precedingLetter == 't') && measure(sb, stemLength) > 1;
    };

    // Sorted by descending suffix length: within a step only the longest matching
    // suffix is ever tested (see applyLongestMatchRule). Order among same-length
    // entries does not matter since a word cannot end in two different suffixes of
    // equal length at once.
    private static final List<SuffixRule> STEP2_RULES = List.of(
            new SuffixRule("ization", "ize", MEASURE_GT_0),
            new SuffixRule("ational", "ate", MEASURE_GT_0),
            new SuffixRule("iveness", "ive", MEASURE_GT_0),
            new SuffixRule("fulness", "ful", MEASURE_GT_0),
            new SuffixRule("ousness", "ous", MEASURE_GT_0),
            new SuffixRule("tional", "tion", MEASURE_GT_0),
            new SuffixRule("biliti", "ble", MEASURE_GT_0),
            new SuffixRule("ation", "ate", MEASURE_GT_0),
            new SuffixRule("entli", "ent", MEASURE_GT_0),
            new SuffixRule("ousli", "ous", MEASURE_GT_0),
            new SuffixRule("alism", "al", MEASURE_GT_0),
            new SuffixRule("aliti", "al", MEASURE_GT_0),
            new SuffixRule("iviti", "ive", MEASURE_GT_0),
            new SuffixRule("enci", "ence", MEASURE_GT_0),
            new SuffixRule("anci", "ance", MEASURE_GT_0),
            new SuffixRule("izer", "ize", MEASURE_GT_0),
            new SuffixRule("alli", "al", MEASURE_GT_0),
            new SuffixRule("ator", "ate", MEASURE_GT_0),
            new SuffixRule("logi", "log", MEASURE_GT_0), // departure: added, not in the 1980 paper's table
            new SuffixRule("eli", "e", MEASURE_GT_0),
            new SuffixRule("bli", "ble", MEASURE_GT_0) // departure: replaces the paper's narrower "abli" -> "able"
    );

    private static final List<SuffixRule> STEP3_RULES = List.of(
            new SuffixRule("icate", "ic", MEASURE_GT_0),
            new SuffixRule("ative", "", MEASURE_GT_0),
            new SuffixRule("alize", "al", MEASURE_GT_0),
            new SuffixRule("iciti", "ic", MEASURE_GT_0),
            new SuffixRule("ical", "ic", MEASURE_GT_0),
            new SuffixRule("ness", "", MEASURE_GT_0),
            new SuffixRule("ful", "", MEASURE_GT_0)
    );

    private static final List<SuffixRule> STEP4_RULES = List.of(
            new SuffixRule("ement", "", MEASURE_GT_1),
            new SuffixRule("ance", "", MEASURE_GT_1),
            new SuffixRule("ence", "", MEASURE_GT_1),
            new SuffixRule("able", "", MEASURE_GT_1),
            new SuffixRule("ible", "", MEASURE_GT_1),
            new SuffixRule("ment", "", MEASURE_GT_1),
            new SuffixRule("ant", "", MEASURE_GT_1),
            new SuffixRule("ent", "", MEASURE_GT_1),
            new SuffixRule("ion", "", STEP4_ION_CONDITION),
            new SuffixRule("ism", "", MEASURE_GT_1),
            new SuffixRule("ate", "", MEASURE_GT_1),
            new SuffixRule("iti", "", MEASURE_GT_1),
            new SuffixRule("ous", "", MEASURE_GT_1),
            new SuffixRule("ive", "", MEASURE_GT_1),
            new SuffixRule("ize", "", MEASURE_GT_1),
            new SuffixRule("al", "", MEASURE_GT_1),
            new SuffixRule("er", "", MEASURE_GT_1),
            new SuffixRule("ic", "", MEASURE_GT_1),
            new SuffixRule("ou", "", MEASURE_GT_1)
    );

    /** Applies the algorithm to a single already-lowercased token. */
    String stem(String word) {
        if (word.length() <= 2) {
            // A word of two letters or fewer has measure 0 under every possible split;
            // none of steps 1-5 can fire, so skip the work entirely.
            return word;
        }

        StringBuilder sb = new StringBuilder(word);
        step1a(sb);
        step1b(sb);
        step1c(sb);
        step2(sb);
        step3(sb);
        step4(sb);
        step5a(sb);
        step5b(sb);
        return sb.toString();
    }

    // --- Step 1a: plural / third-person suffixes -----------------------------------

    void step1a(StringBuilder sb) {
        if (endsWith(sb, "sses")) {
            sb.setLength(sb.length() - 2); // SSES -> SS
        } else if (endsWith(sb, "ies")) {
            replaceSuffix(sb, 3, "i"); // IES -> I
        } else if (endsWith(sb, "ss")) {
            // SS -> SS, unchanged.
        } else if (endsWith(sb, "s")) {
            replaceSuffix(sb, 1, ""); // S -> (delete)
        }
    }

    // --- Step 1b: -EED / -ED / -ING, with clean-up ----------------------------------

    void step1b(StringBuilder sb) {
        if (endsWith(sb, "eed")) {
            int stemLength = sb.length() - 3;
            if (measure(sb, stemLength) > 0) {
                replaceSuffix(sb, 3, "ee"); // (m>0) EED -> EE
            }
            return; // EED is mutually exclusive with the ED/ING branch below.
        }

        boolean suffixRemoved = false;
        if (endsWith(sb, "ed")) {
            int stemLength = sb.length() - 2;
            if (containsVowel(sb, stemLength)) {
                sb.setLength(stemLength); // (*v*) ED -> (delete)
                suffixRemoved = true;
            }
        } else if (endsWith(sb, "ing")) {
            int stemLength = sb.length() - 3;
            if (containsVowel(sb, stemLength)) {
                sb.setLength(stemLength); // (*v*) ING -> (delete)
                suffixRemoved = true;
            }
        }

        if (!suffixRemoved) {
            return;
        }

        if (endsWith(sb, "at") || endsWith(sb, "bl") || endsWith(sb, "iz")) {
            sb.append('e'); // conflat(ed) -> conflate, troubl(ing) -> trouble
        } else if (endsWithDoubleConsonant(sb) && !endsWithAnyOf(sb, 'l', 's', 'z')) {
            sb.setLength(sb.length() - 1); // hopp(ing) -> hop, tann(ed) -> tan
        } else if (measure(sb, sb.length()) == 1 && endsCvc(sb, sb.length())) {
            sb.append('e'); // fil(ing) -> file
        }
    }

    // --- Step 1c: terminal Y --------------------------------------------------------

    void step1c(StringBuilder sb) {
        if (endsWith(sb, "y")) {
            int stemLength = sb.length() - 1;
            if (containsVowel(sb, stemLength)) {
                sb.setCharAt(sb.length() - 1, 'i'); // (*v*) Y -> I, e.g. happy -> happi
            }
        }
    }

    // --- Step 2: derivational suffixes ----------------------------------------------

    void step2(StringBuilder sb) {
        applyLongestMatchRule(sb, STEP2_RULES);
    }

    // --- Step 3: derivational suffixes ----------------------------------------------

    void step3(StringBuilder sb) {
        applyLongestMatchRule(sb, STEP3_RULES);
    }

    // --- Step 4: (m>1) suffixes, dropped outright -----------------------------------

    void step4(StringBuilder sb) {
        applyLongestMatchRule(sb, STEP4_RULES);
    }

    // --- Step 5a / 5b: final E and double L -----------------------------------------

    void step5a(StringBuilder sb) {
        if (!endsWith(sb, "e")) {
            return;
        }
        int stemLength = sb.length() - 1;
        int m = measure(sb, stemLength);
        if (m > 1 || (m == 1 && !endsCvc(sb, stemLength))) {
            sb.setLength(stemLength);
        }
    }

    void step5b(StringBuilder sb) {
        if (measure(sb, sb.length()) > 1 && endsWithDoubleConsonant(sb) && sb.charAt(sb.length() - 1) == 'l') {
            sb.setLength(sb.length() - 1);
        }
    }

    // --- Shared rule application helpers --------------------------------------------

    /**
     * Applies Porter's "longest matching suffix wins" rule for one step: {@code rules}
     * must be sorted by descending suffix length. Only the first (thus longest)
     * suffix that matches the word is ever tested; if its condition holds, the
     * replacement is applied, and if it does not, nothing happens — no shorter
     * suffix later in the list is tried, even if it would otherwise match. This is
     * the fix for the defect this class used to have: falling through a failed
     * longest match to try a shorter one (e.g. "document" incorrectly losing its
     * "-ent" because the longer "-ment" rule's condition failed first).
     */
    private static void applyLongestMatchRule(StringBuilder sb, List<SuffixRule> rules) {
        for (SuffixRule rule : rules) {
            if (endsWith(sb, rule.suffix())) {
                int stemLength = sb.length() - rule.suffix().length();
                if (rule.condition().test(sb, stemLength)) {
                    replaceSuffix(sb, rule.suffix().length(), rule.replacement());
                }
                return;
            }
        }
    }

    private static void replaceSuffix(StringBuilder sb, int suffixLength, String replacement) {
        sb.setLength(sb.length() - suffixLength);
        sb.append(replacement);
    }

    private static boolean endsWith(StringBuilder sb, String suffix) {
        int n = sb.length();
        int suffixLength = suffix.length();
        if (suffixLength > n) {
            return false;
        }
        for (int i = 0; i < suffixLength; i++) {
            if (sb.charAt(n - suffixLength + i) != suffix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean endsWithAnyOf(StringBuilder sb, char... candidates) {
        char last = sb.charAt(sb.length() - 1);
        for (char candidate : candidates) {
            if (last == candidate) {
                return true;
            }
        }
        return false;
    }

    private static boolean endsWithDoubleConsonant(StringBuilder sb) {
        int n = sb.length();
        if (n < 2) {
            return false;
        }
        return sb.charAt(n - 1) == sb.charAt(n - 2)
                && isConsonant(sb, n - 1)
                && isConsonant(sb, n - 2);
    }

    /** *o: stem ends consonant-vowel-consonant, second consonant not W, X, or Y. */
    private static boolean endsCvc(StringBuilder sb, int limit) {
        if (limit < 3) {
            return false;
        }
        if (!isConsonant(sb, limit - 3) || isConsonant(sb, limit - 2) || !isConsonant(sb, limit - 1)) {
            return false;
        }
        char lastLetter = sb.charAt(limit - 1);
        return lastLetter != 'w' && lastLetter != 'x' && lastLetter != 'y';
    }

    /** *v*: the region {@code sb[0, limit)} contains at least one vowel. */
    private static boolean containsVowel(StringBuilder sb, int limit) {
        for (int i = 0; i < limit; i++) {
            if (!isConsonant(sb, i)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Measure {@code m} of {@code sb[0, limit)}: the number of vowel-consonant (VC)
     * transitions once any leading consonant run is skipped, matching the word form
     * {@code [C](VC)^m[V]} from Porter's 1980 paper.
     */
    private static int measure(StringBuilder sb, int limit) {
        int i = 0;
        while (i < limit && isConsonant(sb, i)) {
            i++;
        }
        int m = 0;
        while (i < limit) {
            while (i < limit && !isConsonant(sb, i)) {
                i++;
            }
            if (i >= limit) {
                break;
            }
            while (i < limit && isConsonant(sb, i)) {
                i++;
            }
            m++;
        }
        return m;
    }

    /**
     * A consonant is any letter other than A, E, I, O, U, and other than Y preceded by
     * a consonant (so Y is a consonant when it has a vowel before it or starts the
     * word, and a vowel otherwise — TOY: T, Y are consonants; SYZYGY: S, Z, G are).
     */
    private static boolean isConsonant(StringBuilder sb, int i) {
        char c = sb.charAt(i);
        return switch (c) {
            case 'a', 'e', 'i', 'o', 'u' -> false;
            case 'y' -> i == 0 || !isConsonant(sb, i - 1);
            default -> true;
        };
    }
}
