/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.langchain4j.agent.api.guardrails;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

/**
 * Input guardrail that validates the language of user messages.
 *
 * <p>
 * This guardrail uses character set analysis to detect the script/language family of input text. It can be configured
 * to allow only specific languages or block specific languages.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>{@code
 * // Allow only English
 * LanguageGuardrail guardrail = LanguageGuardrail.allowOnly(Language.ENGLISH);
 *
 * // Allow English and Spanish
 * LanguageGuardrail guardrail = LanguageGuardrail.builder()
 *         .allowedLanguages(Language.ENGLISH, Language.LATIN_SCRIPT)
 *         .build();
 * }</pre>
 *
 * @since 4.17.0
 */
public class LanguageGuardrail implements InputGuardrail {

    /**
     * Detected language/script categories.
     */
    public enum Language {
        /** English and basic Latin characters */
        ENGLISH(Pattern.compile("^[\\p{ASCII}\\s\\p{Punct}]+$")),

        /** Latin script languages (English, Spanish, French, German, etc.) */
        LATIN_SCRIPT(Pattern.compile("[\\p{IsLatin}]")),

        /** Cyrillic script (Russian, Ukrainian, etc.) */
        CYRILLIC(Pattern.compile("[\\p{IsCyrillic}]")),

        /** Chinese characters */
        CHINESE(Pattern.compile("[\\p{IsHan}]")),

        /** Japanese (Hiragana, Katakana, Kanji) */
        JAPANESE(Pattern.compile("[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}]")),

        /** Korean (Hangul) */
        KOREAN(Pattern.compile("[\\p{IsHangul}]")),

        /** Arabic script */
        ARABIC(Pattern.compile("[\\p{IsArabic}]")),

        /** Hebrew script */
        HEBREW(Pattern.compile("[\\p{IsHebrew}]")),

        /** Greek script */
        GREEK(Pattern.compile("[\\p{IsGreek}]")),

        /** Thai script */
        THAI(Pattern.compile("[\\p{IsThai}]")),

        /** Devanagari script (Hindi, Sanskrit, etc.) */
        DEVANAGARI(Pattern.compile("[\\p{IsDevanagari}]"));

        private final Pattern pattern;

        Language(Pattern pattern) {
            this.pattern = pattern;
        }

        public Pattern getPattern() {
            return pattern;
        }

        /**
         * Checks if the text contains characters from this language/script.
         */
        public boolean isPresent(String text) {
            return pattern.matcher(text).find();
        }
    }

    private final Set<Language> allowedLanguages;
    private final Set<Language> blockedLanguages;
    private final boolean allowMixed;
    private final double minLanguageRatio;

    /**
     * Creates a guardrail that allows all languages.
     */
    public LanguageGuardrail() {
        this(new HashSet<>(), new HashSet<>(), true, 0.0);
    }

    /**
     * Creates a guardrail with specific configuration.
     *
     * @param allowedLanguages languages to allow (empty = allow all)
     * @param blockedLanguages languages to block
     * @param allowMixed       whether to allow mixed language content
     * @param minLanguageRatio minimum ratio of allowed language characters (0.0-1.0)
     */
    public LanguageGuardrail(Set<Language> allowedLanguages, Set<Language> blockedLanguages,
                             boolean allowMixed, double minLanguageRatio) {
        this.allowedLanguages = new HashSet<>(allowedLanguages);
        this.blockedLanguages = new HashSet<>(blockedLanguages);
        this.allowMixed = allowMixed;
        this.minLanguageRatio = minLanguageRatio;
    }

    /**
     * Creates a guardrail that only allows specific languages.
     *
     * @param  languages the languages to allow
     * @return           a new LanguageGuardrail instance
     */
    public static LanguageGuardrail allowOnly(Language... languages) {
        return builder().allowedLanguages(languages).build();
    }

    /**
     * Creates a guardrail that blocks specific languages.
     *
     * @param  languages the languages to block
     * @return           a new LanguageGuardrail instance
     */
    public static LanguageGuardrail block(Language... languages) {
        return builder().blockedLanguages(languages).build();
    }

    /**
     * Creates a new builder for configuring the guardrail.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        if (userMessage == null || userMessage.singleText() == null) {
            return success();
        }

        String text = userMessage.singleText();

        // Check for blocked languages
        for (Language blocked : blockedLanguages) {
            if (blocked.isPresent(text)) {
                return failure(String.format(
                        "Message contains blocked language/script: %s", blocked.name()));
            }
        }

        // If no allowed languages specified, allow all (that aren't blocked)
        if (allowedLanguages.isEmpty()) {
            return success();
        }

        // Check if any allowed language is present
        boolean hasAllowedLanguage = false;
        Set<Language> detectedLanguages = new HashSet<>();

        for (Language lang : Language.values()) {
            if (lang.isPresent(text)) {
                detectedLanguages.add(lang);
                if (allowedLanguages.contains(lang)) {
                    hasAllowedLanguage = true;
                }
            }
        }

        if (!hasAllowedLanguage) {
            return failure(String.format(
                    "Message language not allowed. Allowed languages: %s", allowedLanguages));
        }

        // Check for mixed content if not allowed
        if (!allowMixed && detectedLanguages.size() > 1) {
            // Check if all detected languages are in allowed set
            for (Language detected : detectedLanguages) {
                if (!allowedLanguages.contains(detected) && detected != Language.ENGLISH) {
                    return failure("Mixed language content is not allowed.");
                }
            }
        }

        return success();
    }

    /**
     * @return the set of allowed languages
     */
    public Set<Language> getAllowedLanguages() {
        return new HashSet<>(allowedLanguages);
    }

    /**
     * @return the set of blocked languages
     */
    public Set<Language> getBlockedLanguages() {
        return new HashSet<>(blockedLanguages);
    }

    /**
     * Builder for creating LanguageGuardrail instances.
     */
    public static class Builder {
        private Set<Language> allowedLanguages = new HashSet<>();
        private Set<Language> blockedLanguages = new HashSet<>();
        private boolean allowMixed = true;
        private double minLanguageRatio = 0.0;

        /**
         * Sets the allowed languages.
         *
         * @param  languages the languages to allow
         * @return           this builder
         */
        public Builder allowedLanguages(Language... languages) {
            this.allowedLanguages.addAll(Arrays.asList(languages));
            return this;
        }

        /**
         * Sets the blocked languages.
         *
         * @param  languages the languages to block
         * @return           this builder
         */
        public Builder blockedLanguages(Language... languages) {
            this.blockedLanguages.addAll(Arrays.asList(languages));
            return this;
        }

        /**
         * Sets whether mixed language content is allowed.
         *
         * @param  allowMixed true to allow mixed content
         * @return            this builder
         */
        public Builder allowMixed(boolean allowMixed) {
            this.allowMixed = allowMixed;
            return this;
        }

        /**
         * Sets the minimum ratio of allowed language characters.
         *
         * @param  ratio minimum ratio (0.0-1.0)
         * @return       this builder
         */
        public Builder minLanguageRatio(double ratio) {
            this.minLanguageRatio = ratio;
            return this;
        }

        /**
         * Builds the guardrail instance.
         *
         * @return a new LanguageGuardrail instance
         */
        public LanguageGuardrail build() {
            return new LanguageGuardrail(allowedLanguages, blockedLanguages, allowMixed, minLanguageRatio);
        }
    }
}
