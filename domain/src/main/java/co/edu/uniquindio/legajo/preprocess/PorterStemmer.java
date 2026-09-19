package co.edu.uniquindio.legajo.preprocess;

/**
 * Hand-written implementation of the original Porter (1980) stemming algorithm
 * ("An algorithm for suffix stripping", Program 14(3), pp. 130-137). TRD §3.3 lists
 * "Porter opcional" among the components domain must implement itself (R-02): no
 * Lucene, OpenNLP, CoreNLP, spaCy, NLTK, or Snowball stemmer dependency is used here.
 *
 * <p>The algorithm reduces a word in up to seven ordered steps (1a, 1b, 1c, 2, 3, 4,
 * 5a, 5b), each conditioned on the "measure" {@code m} of the stem — the number of
 * vowel-consonant transitions in {@code [C](VC)^m[V]} — or on simple structural
 * predicates ({@code *v*}: stem contains a vowel; {@code *d}: stem ends in a double
 * consonant; {@code *o}: stem ends consonant-vowel-consonant, second consonant not
 * W, X, or Y). This class is package-private: {@link TextPreprocessor} is the only
 * caller, gated by the {@code preprocess.stemming} flag (default {@code false}).
 */
final class PorterStemmer {

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
    // Longer suffixes that are themselves tails of another rule's suffix are checked
    // first (IZATION before ATION, ATIONAL before TIONAL) so the more specific rule
    // always wins; see Porter (1980) step 2.

    void step2(StringBuilder sb) {
        if (applyConditionalRule(sb, "ization", "ize")) return;
        if (applyConditionalRule(sb, "ational", "ate")) return;
        if (applyConditionalRule(sb, "ation", "ate")) return;
        if (applyConditionalRule(sb, "tional", "tion")) return;
        if (applyConditionalRule(sb, "enci", "ence")) return;
        if (applyConditionalRule(sb, "anci", "ance")) return;
        if (applyConditionalRule(sb, "izer", "ize")) return;
        if (applyConditionalRule(sb, "abli", "able")) return;
        if (applyConditionalRule(sb, "alli", "al")) return;
        if (applyConditionalRule(sb, "entli", "ent")) return;
        if (applyConditionalRule(sb, "eli", "e")) return;
        if (applyConditionalRule(sb, "ousli", "ous")) return;
        if (applyConditionalRule(sb, "ator", "ate")) return;
        if (applyConditionalRule(sb, "alism", "al")) return;
        if (applyConditionalRule(sb, "iveness", "ive")) return;
        if (applyConditionalRule(sb, "fulness", "ful")) return;
        if (applyConditionalRule(sb, "ousness", "ous")) return;
        if (applyConditionalRule(sb, "aliti", "al")) return;
        if (applyConditionalRule(sb, "iviti", "ive")) return;
        applyConditionalRule(sb, "biliti", "ble");
    }

    // --- Step 3: derivational suffixes ----------------------------------------------

    void step3(StringBuilder sb) {
        if (applyConditionalRule(sb, "icate", "ic")) return;
        if (applyConditionalRule(sb, "ative", "")) return;
        if (applyConditionalRule(sb, "alize", "al")) return;
        if (applyConditionalRule(sb, "iciti", "ic")) return;
        if (applyConditionalRule(sb, "ical", "ic")) return;
        if (applyConditionalRule(sb, "ful", "")) return;
        applyConditionalRule(sb, "ness", "");
    }

    // --- Step 4: (m>1) suffixes, dropped outright -----------------------------------

    void step4(StringBuilder sb) {
        if (applyConditionalRuleAbove1(sb, "al", "")) return;
        if (applyConditionalRuleAbove1(sb, "ance", "")) return;
        if (applyConditionalRuleAbove1(sb, "ence", "")) return;
        if (applyConditionalRuleAbove1(sb, "er", "")) return;
        if (applyConditionalRuleAbove1(sb, "ic", "")) return;
        if (applyConditionalRuleAbove1(sb, "able", "")) return;
        if (applyConditionalRuleAbove1(sb, "ible", "")) return;
        if (applyConditionalRuleAbove1(sb, "ant", "")) return;
        if (applyConditionalRuleAbove1(sb, "ement", "")) return;
        if (applyConditionalRuleAbove1(sb, "ment", "")) return;
        if (applyConditionalRuleAbove1(sb, "ent", "")) return;
        if (step4Ion(sb)) return;
        if (applyConditionalRuleAbove1(sb, "ou", "")) return;
        if (applyConditionalRuleAbove1(sb, "ism", "")) return;
        if (applyConditionalRuleAbove1(sb, "ate", "")) return;
        if (applyConditionalRuleAbove1(sb, "iti", "")) return;
        if (applyConditionalRuleAbove1(sb, "ous", "")) return;
        if (applyConditionalRuleAbove1(sb, "ive", "")) return;
        applyConditionalRuleAbove1(sb, "ize", "");
    }

    /** (m>1 and (*S or *T)) ION -> (delete); the only step-4 rule with an extra letter test. */
    private boolean step4Ion(StringBuilder sb) {
        if (!endsWith(sb, "ion")) {
            return false;
        }
        int stemLength = sb.length() - 3;
        if (stemLength <= 0) {
            return false;
        }
        char precedingLetter = sb.charAt(stemLength - 1);
        if ((precedingLetter != 's' && precedingLetter != 't') || measure(sb, stemLength) <= 1) {
            return false;
        }
        sb.setLength(stemLength);
        return true;
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

    /** (m>0) suffix -> replacement. */
    private boolean applyConditionalRule(StringBuilder sb, String suffix, String replacement) {
        if (!endsWith(sb, suffix)) {
            return false;
        }
        int stemLength = sb.length() - suffix.length();
        if (measure(sb, stemLength) <= 0) {
            return false;
        }
        replaceSuffix(sb, suffix.length(), replacement);
        return true;
    }

    /** (m>1) suffix -> replacement. */
    private boolean applyConditionalRuleAbove1(StringBuilder sb, String suffix, String replacement) {
        if (!endsWith(sb, suffix)) {
            return false;
        }
        int stemLength = sb.length() - suffix.length();
        if (measure(sb, stemLength) <= 1) {
            return false;
        }
        replaceSuffix(sb, suffix.length(), replacement);
        return true;
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
