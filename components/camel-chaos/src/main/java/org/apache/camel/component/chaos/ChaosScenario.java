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
package org.apache.camel.component.chaos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A chaos scenario: the rules of an experiment in a file of the project, such as {@code chaos/outage.chaos}, so it can
 * be run again and shared. It has one rule per line, in the one-line form of {@code camel.chaos.rules}; a line that
 * starts with {@code #} is a comment, and a line {@code seed=<number>} fixes the random draws of its rules, so the
 * scenario fails the same messages each time. The phases of an experiment come from {@code from=} and {@code until=} of
 * its rules.
 *
 * <pre>
 * # the stock service is down for a minute, then slow
 * seed=42
 * endpoint=http:stock* error status=503 until=1m
 * endpoint=http:stock* delay duration=2s from=1m until=2m
 * route=ship expect=ship:completed>=10 within=3m
 * </pre>
 *
 * @since 4.24
 */
public final class ChaosScenario {

    /** The extension of a scenario file. */
    public static final String EXTENSION = ".chaos";

    private final String name;
    private final Long seed;
    private final List<String> rules;
    private final List<Integer> lines;

    private ChaosScenario(String name, Long seed, List<String> rules, List<Integer> lines) {
        this.name = name;
        this.seed = seed;
        this.rules = Collections.unmodifiableList(rules);
        this.lines = Collections.unmodifiableList(lines);
    }

    /**
     * Reads a scenario. The rules are not checked here, only the lines: {@link ChaosManager#loadScenario} checks them,
     * all of them before any is added.
     *
     * @param  name                     the name of the scenario, such as the name of its file, for the messages
     * @param  text                     the content of the scenario
     * @return                          the scenario
     * @throws IllegalArgumentException when a seed line is not valid, or there are no rules
     */
    public static ChaosScenario parse(String name, String text) {
        Long seed = null;
        List<String> rules = new ArrayList<>();
        List<Integer> lines = new ArrayList<>();
        String[] all = text == null ? new String[0] : text.split("\\r?\\n", -1);
        for (int i = 0; i < all.length; i++) {
            String line = all[i].strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("seed=")) {
                if (seed != null) {
                    throw new IllegalArgumentException(where(name, i + 1) + "the seed is given twice");
                }
                String value = line.substring(5).strip();
                try {
                    seed = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(where(name, i + 1) + "seed=" + value + " is not a whole number", e);
                }
                continue;
            }
            rules.add(line);
            lines.add(i + 1);
        }
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("The chaos scenario " + name + " has no rules");
        }
        return new ChaosScenario(name, seed, rules, lines);
    }

    /** The name of the scenario, such as {@code outage.chaos}. */
    public String getName() {
        return name;
    }

    /** The seed of the scenario, or null when it has none (its rules then draw from the seed of the application). */
    public Long getSeed() {
        return seed;
    }

    /** The rules, in their order. */
    public List<String> getRules() {
        return rules;
    }

    /** The line of the file a rule is on, for the message of a rule that is not valid. */
    int lineOf(int index) {
        return lines.get(index);
    }

    String where(int line) {
        return where(name, line);
    }

    private static String where(String name, int line) {
        return "Chaos scenario " + name + ", line " + line + ": ";
    }
}
