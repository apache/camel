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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;

/**
 * Factory and helper class for creating and configuring guardrails.
 *
 * <p>
 * This class provides convenient static factory methods for creating common guardrail configurations, as well as a
 * fluent builder API for constructing {@link AgentConfiguration} with guardrails.
 * </p>
 *
 * <p>
 * Example usage with factory methods:
 * </p>
 *
 * <pre>{@code
 * AgentConfiguration config = new AgentConfiguration()
 *         .withChatModel(chatModel)
 *         .withInputGuardrailClasses(Guardrails.defaultInputGuardrails())
 *         .withOutputGuardrailClasses(Guardrails.defaultOutputGuardrails());
 * }</pre>
 *
 * <p>
 * Example usage with builder:
 * </p>
 *
 * <pre>{@code
 * AgentConfiguration config = Guardrails.configure()
 *         .withChatModel(chatModel)
 *         .inputGuardrails()
 *         .piiDetection()
 *         .promptInjection()
 *         .maxLength(5000)
 *         .outputGuardrails()
 *         .sensitiveDataRedaction()
 *         .jsonFormat()
 *         .build();
 * }</pre>
 *
 * @since 4.17.0
 */
public final class Guardrails {

    private Guardrails() {
        // Utility class
    }

    // ==================== Default Guardrail Sets ====================

    /**
     * Returns the default set of input guardrail classes for secure AI interactions.
     *
     * <p>
     * Includes:
     * </p>
     * <ul>
     * <li>{@link InputLengthGuardrail} - Prevents excessively long inputs</li>
     * <li>{@link PiiDetectorGuardrail} - Detects personal information</li>
     * <li>{@link PromptInjectionGuardrail} - Detects injection attacks</li>
     * </ul>
     *
     * @return list of default input guardrail classes
     */
    public static List<Class<?>> defaultInputGuardrails() {
        return Arrays.asList(
                InputLengthGuardrail.class,
                PiiDetectorGuardrail.class,
                PromptInjectionGuardrail.class);
    }

    /**
     * Returns the default set of output guardrail classes for secure AI responses.
     *
     * <p>
     * Includes:
     * </p>
     * <ul>
     * <li>{@link OutputLengthGuardrail} - Ensures responses aren't too long</li>
     * <li>{@link SensitiveDataOutputGuardrail} - Detects leaked secrets</li>
     * </ul>
     *
     * @return list of default output guardrail classes
     */
    public static List<Class<?>> defaultOutputGuardrails() {
        return Arrays.asList(
                OutputLengthGuardrail.class,
                SensitiveDataOutputGuardrail.class);
    }

    /**
     * Returns a minimal set of input guardrails for basic protection.
     *
     * @return list of minimal input guardrail classes
     */
    public static List<Class<?>> minimalInputGuardrails() {
        return Arrays.asList(InputLengthGuardrail.class);
    }

    /**
     * Returns a strict set of input guardrails for high-security scenarios.
     *
     * @return list of strict input guardrail classes
     */
    public static List<Class<?>> strictInputGuardrails() {
        return Arrays.asList(
                InputLengthGuardrail.class,
                PiiDetectorGuardrail.class,
                PromptInjectionGuardrail.class,
                KeywordFilterGuardrail.class);
    }

    // ==================== Input Guardrail Factories ====================

    /**
     * Creates an input length guardrail with default limits.
     *
     * @return a new InputLengthGuardrail instance
     */
    public static InputLengthGuardrail inputLength() {
        return new InputLengthGuardrail();
    }

    /**
     * Creates an input length guardrail with custom max length.
     *
     * @param  maxChars maximum allowed characters
     * @return          a new InputLengthGuardrail instance
     */
    public static InputLengthGuardrail inputLength(int maxChars) {
        return InputLengthGuardrail.maxLength(maxChars);
    }

    /**
     * Creates a PII detector guardrail with default settings.
     *
     * @return a new PiiDetectorGuardrail instance
     */
    public static PiiDetectorGuardrail piiDetector() {
        return new PiiDetectorGuardrail();
    }

    /**
     * Creates a PII detector guardrail builder for custom configuration.
     *
     * @return a new PiiDetectorGuardrail.Builder instance
     */
    public static PiiDetectorGuardrail.Builder piiDetectorBuilder() {
        return PiiDetectorGuardrail.builder();
    }

    /**
     * Creates a prompt injection guardrail with default settings.
     *
     * @return a new PromptInjectionGuardrail instance
     */
    public static PromptInjectionGuardrail promptInjection() {
        return new PromptInjectionGuardrail();
    }

    /**
     * Creates a strict prompt injection guardrail.
     *
     * @return a new strict PromptInjectionGuardrail instance
     */
    public static PromptInjectionGuardrail promptInjectionStrict() {
        return PromptInjectionGuardrail.strict();
    }

    /**
     * Creates a keyword filter guardrail that blocks specified words.
     *
     * @param  words the words to block
     * @return       a new KeywordFilterGuardrail instance
     */
    public static KeywordFilterGuardrail keywordFilter(String... words) {
        return KeywordFilterGuardrail.blocking(words);
    }

    // ==================== Output Guardrail Factories ====================

    /**
     * Creates an output length guardrail with default limits.
     *
     * @return a new OutputLengthGuardrail instance
     */
    public static OutputLengthGuardrail outputLength() {
        return new OutputLengthGuardrail();
    }

    /**
     * Creates an output length guardrail with custom max length.
     *
     * @param  maxChars maximum allowed characters
     * @return          a new OutputLengthGuardrail instance
     */
    public static OutputLengthGuardrail outputLength(int maxChars) {
        return OutputLengthGuardrail.maxLength(maxChars);
    }

    /**
     * Creates an output length guardrail that truncates on overflow.
     *
     * @param  maxChars maximum allowed characters before truncation
     * @return          a new OutputLengthGuardrail instance
     */
    public static OutputLengthGuardrail outputLengthTruncating(int maxChars) {
        return OutputLengthGuardrail.truncatingAt(maxChars);
    }

    /**
     * Creates a sensitive data guardrail with default settings (blocks on detection).
     *
     * @return a new SensitiveDataOutputGuardrail instance
     */
    public static SensitiveDataOutputGuardrail sensitiveData() {
        return new SensitiveDataOutputGuardrail();
    }

    /**
     * Creates a sensitive data guardrail that redacts instead of blocking.
     *
     * @return a new SensitiveDataOutputGuardrail instance
     */
    public static SensitiveDataOutputGuardrail sensitiveDataRedacting() {
        return SensitiveDataOutputGuardrail.redacting();
    }

    /**
     * Creates a JSON format guardrail with default settings.
     *
     * @return a new JsonFormatGuardrail instance
     */
    public static JsonFormatGuardrail jsonFormat() {
        return new JsonFormatGuardrail();
    }

    /**
     * Creates a JSON format guardrail with required fields.
     *
     * @param  fields the required field names
     * @return        a new JsonFormatGuardrail instance
     */
    public static JsonFormatGuardrail jsonFormatWithFields(String... fields) {
        return JsonFormatGuardrail.requireFields(fields);
    }

    /**
     * Creates a keyword output filter that blocks specified words.
     *
     * @param  words the words to block
     * @return       a new KeywordOutputFilterGuardrail instance
     */
    public static KeywordOutputFilterGuardrail outputKeywordFilter(String... words) {
        return KeywordOutputFilterGuardrail.blocking(words);
    }

    /**
     * Creates a keyword output filter that redacts specified words.
     *
     * @param  words the words to redact
     * @return       a new KeywordOutputFilterGuardrail instance
     */
    public static KeywordOutputFilterGuardrail outputKeywordFilterRedacting(String... words) {
        return KeywordOutputFilterGuardrail.redacting(words);
    }

    // ==================== Fluent Configuration Builder ====================

    /**
     * Creates a new guardrails configuration builder.
     *
     * @return a new ConfigurationBuilder instance
     */
    public static ConfigurationBuilder configure() {
        return new ConfigurationBuilder();
    }

    /**
     * Fluent builder for configuring an AgentConfiguration with guardrails.
     */
    public static class ConfigurationBuilder {
        private final AgentConfiguration config = new AgentConfiguration();
        private final List<Class<? extends InputGuardrail>> inputGuardrails = new ArrayList<>();
        private final List<Class<? extends OutputGuardrail>> outputGuardrails = new ArrayList<>();

        /**
         * Adds an input guardrail class.
         *
         * @param  guardrailClass the input guardrail class
         * @return                this builder
         */
        public ConfigurationBuilder withInputGuardrail(Class<? extends InputGuardrail> guardrailClass) {
            inputGuardrails.add(guardrailClass);
            return this;
        }

        /**
         * Adds an output guardrail class.
         *
         * @param  guardrailClass the output guardrail class
         * @return                this builder
         */
        public ConfigurationBuilder withOutputGuardrail(Class<? extends OutputGuardrail> guardrailClass) {
            outputGuardrails.add(guardrailClass);
            return this;
        }

        /**
         * Adds the default input guardrails.
         *
         * @return this builder
         */
        @SuppressWarnings("unchecked")
        public ConfigurationBuilder withDefaultInputGuardrails() {
            for (Class<?> clazz : defaultInputGuardrails()) {
                inputGuardrails.add((Class<? extends InputGuardrail>) clazz);
            }
            return this;
        }

        /**
         * Adds the default output guardrails.
         *
         * @return this builder
         */
        @SuppressWarnings("unchecked")
        public ConfigurationBuilder withDefaultOutputGuardrails() {
            for (Class<?> clazz : defaultOutputGuardrails()) {
                outputGuardrails.add((Class<? extends OutputGuardrail>) clazz);
            }
            return this;
        }

        /**
         * Adds all default guardrails (both input and output).
         *
         * @return this builder
         */
        public ConfigurationBuilder withDefaultGuardrails() {
            return withDefaultInputGuardrails().withDefaultOutputGuardrails();
        }

        /**
         * Adds PII detection input guardrail.
         *
         * @return this builder
         */
        public ConfigurationBuilder withPiiDetection() {
            return withInputGuardrail(PiiDetectorGuardrail.class);
        }

        /**
         * Adds prompt injection detection input guardrail.
         *
         * @return this builder
         */
        public ConfigurationBuilder withPromptInjectionDetection() {
            return withInputGuardrail(PromptInjectionGuardrail.class);
        }

        /**
         * Adds input length validation guardrail.
         *
         * @return this builder
         */
        public ConfigurationBuilder withInputLengthValidation() {
            return withInputGuardrail(InputLengthGuardrail.class);
        }

        /**
         * Adds sensitive data detection output guardrail.
         *
         * @return this builder
         */
        public ConfigurationBuilder withSensitiveDataDetection() {
            return withOutputGuardrail(SensitiveDataOutputGuardrail.class);
        }

        /**
         * Adds JSON format validation output guardrail.
         *
         * @return this builder
         */
        public ConfigurationBuilder withJsonFormatValidation() {
            return withOutputGuardrail(JsonFormatGuardrail.class);
        }

        /**
         * Builds the AgentConfiguration with the configured guardrails.
         *
         * @return a new AgentConfiguration instance
         */
        @SuppressWarnings("unchecked")
        public AgentConfiguration build() {
            if (!inputGuardrails.isEmpty()) {
                config.withInputGuardrailClasses(new ArrayList<>(inputGuardrails));
            }
            if (!outputGuardrails.isEmpty()) {
                config.withOutputGuardrailClasses(new ArrayList<>(outputGuardrails));
            }
            return config;
        }
    }
}
