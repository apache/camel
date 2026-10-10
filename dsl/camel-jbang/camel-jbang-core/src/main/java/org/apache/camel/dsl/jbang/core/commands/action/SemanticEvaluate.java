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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;

@CommandLine.Command(name = "eval", description = "Evaluate a semantic definition or expert operation",
                     sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel semantic eval my-app --evaluation=safe --body='Sample message' --json",
                             "  camel semantic eval my-app --expert=guard --operation=detect --input='Sample message'" })
public class SemanticEvaluate extends SemanticActionCommand {

    // Jsoner accepts trailing commas; command input must use strict JSON parsing.
    private static final ObjectReader JSON_VALUE = new ObjectMapper().readerFor(Object.class)
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    @CommandLine.Option(names = "--evaluation", description = "Named semantic definition to evaluate")
    String evaluation;

    @CommandLine.Option(names = "--body", description = "Sample message body (text, file:path or json:value)")
    String body;

    @CommandLine.Option(names = "--header", description = "Sample header (key=value; use json: for a typed value)")
    List<String> headers;

    @CommandLine.Option(names = "--variable",
                        description = "Sample exchange-local variable (key=value; use json: for a typed value)")
    List<String> variables;

    @CommandLine.Option(names = "--expert", description = "Expert to call directly")
    String expert;

    @CommandLine.Option(names = "--operation", description = "Expert operation to call directly")
    String operation;

    @CommandLine.Option(names = "--input", description = "Direct expert input (text, file:path or json:value)")
    String input;

    @CommandLine.Option(names = "--parameter", description = "Expert parameter (key=value; use json: for a typed value)")
    List<String> parameters;

    @CommandLine.Option(names = "--evaluation-timeout", defaultValue = "50000",
                        description = "Provider evaluation timeout in milliseconds (1-50000)")
    long evaluationTimeout = 50000;

    public SemanticEvaluate(CamelJBangMain main) {
        super(main);
    }

    @Override
    protected JsonObject request() {
        if (evaluationTimeout < 1 || evaluationTimeout > 50000) {
            throw new IllegalArgumentException("--evaluation-timeout must be between 1 and 50000 milliseconds");
        }
        JsonObject request = new JsonObject();
        request.put("action", "semantic-evaluate");
        request.put("timeout", evaluationTimeout);
        if (evaluation != null) {
            requireText(evaluation, "--evaluation");
            if (expert != null || operation != null || input != null || parameters != null) {
                throw new IllegalArgumentException("Choose --evaluation or --expert with --operation and --input");
            }
            request.put("evaluation", evaluation);
            if (body != null) {
                request.put("body", value(body, true));
            }
            if (headers != null) {
                request.put("headers", values(headers, "--header"));
            }
            if (variables != null) {
                JsonObject sampleVariables = values(variables, "--variable");
                if (sampleVariables.keySet().stream().anyMatch(key -> key.contains(":"))) {
                    throw new IllegalArgumentException(
                            "Sample variables must have exchange-local names without a repository prefix");
                }
                request.put("variables", sampleVariables);
            }
        } else {
            if (expert == null && operation == null && input == null) {
                throw new IllegalArgumentException("Choose --evaluation=<name>, or --expert with --operation and --input");
            }
            requireText(expert, "--expert");
            requireText(operation, "--operation");
            if (input == null) {
                throw new IllegalArgumentException("--input is required for a direct expert operation");
            }
            if (body != null || headers != null || variables != null) {
                throw new IllegalArgumentException("--body, --header and --variable require --evaluation");
            }
            request.put("expert", expert);
            request.put("operation", operation);
            request.put("input", value(input, true));
            if (parameters != null) {
                request.put("parameters", values(parameters, "--parameter"));
            }
        }
        return request;
    }

    private static void requireText(String value, String option) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(option + " is required and must not be blank");
        }
    }

    private static JsonObject values(List<String> values, String option) {
        JsonObject answer = new JsonObject();
        for (String entry : values) {
            int separator = entry.indexOf('=');
            if (separator < 1 || entry.substring(0, separator).isBlank()) {
                throw new IllegalArgumentException(option + " must use key=value");
            }
            String key = entry.substring(0, separator);
            if (answer.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate " + option + " key: " + key);
            }
            answer.put(key, value(entry.substring(separator + 1), false));
        }
        return answer;
    }

    private static Object value(String value, boolean allowFile) {
        if (value.startsWith("json:")) {
            try {
                return JSON_VALUE.readValue(value.substring(5));
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid json: value", e);
            }
        }
        if (allowFile && value.startsWith("file:")) {
            try {
                return Files.readString(Path.of(value.substring(5)));
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot read input file: " + value.substring(5), e);
            }
        }
        return value;
    }

    @Override
    protected void render(JsonObject response) {
        for (String key : List.of("evaluation", "status", "value", "probability", "probabilities", "confidence",
                "metadata", "elapsedMillis", "error")) {
            if (response.containsKey(key)) {
                printer().println(key + ": " + Jsoner.serialize(response.get(key)));
            }
        }
    }
}
