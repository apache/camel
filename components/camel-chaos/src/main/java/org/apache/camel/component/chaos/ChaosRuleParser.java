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

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import org.apache.camel.component.chaos.ChaosRule.Fault;
import org.apache.camel.component.chaos.ChaosRule.Hook;
import org.apache.camel.component.chaos.ChaosRule.Target;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.util.TimeUtils;

/**
 * Parses and checks the one-line text form of a rule, such as
 * {@code endpoint=kafka:orders* error exception=java.net.ConnectException rate=0.3 until=1m}. A hand-written scanner:
 * one pass over the text, no regular expressions, and a bounded length. Every check that fails throws an
 * {@link IllegalArgumentException} whose message says what is wrong.
 */
final class ChaosRuleParser {

    /** The longest rule, in characters. */
    static final int MAX_LENGTH = 2000;

    private static final Set<String> GENERAL_KEYS = Set.of("at", "rate", "until");
    private static final Set<String> ERROR_KEYS = Set.of("exception", "message");

    private ChaosRuleParser() {
    }

    /**
     * Splits the rules of {@code camel.chaos.rules}, which are separated by {@code ;}. A {@code ;} inside a quoted
     * value does not separate; empty parts are left out.
     */
    static List<String> splitRules(String text) {
        List<String> answer = new ArrayList<>();
        if (text == null) {
            return answer;
        }
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quote != 0) {
                current.append(ch);
                if (ch == '\\' && i + 1 < text.length()) {
                    current.append(text.charAt(++i));
                } else if (ch == quote) {
                    quote = 0;
                }
            } else if (ch == ';') {
                addRule(answer, current);
                current.setLength(0);
            } else {
                if (ch == '"' || ch == '\'') {
                    quote = ch;
                }
                current.append(ch);
            }
        }
        // an unterminated quote is reported by parse()
        addRule(answer, current);
        return answer;
    }

    private static void addRule(List<String> rules, StringBuilder text) {
        String rule = text.toString().trim();
        if (!rule.isEmpty()) {
            rules.add(rule);
        }
    }

    /**
     * Parses and checks a rule.
     *
     * @param  text                     the rule
     * @param  classResolver            to load the class of {@code exception=}
     * @param  maxDuration              {@code camel.chaos.maxDuration} in milliseconds
     * @return                          the definition of the rule
     * @throws IllegalArgumentException when the rule is not valid, with the reason
     */
    static ChaosRule.Builder parse(String text, ClassResolver classResolver, long maxDuration) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("The chaos rule is empty");
        }
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "The chaos rule is " + text.length() + " characters long, at most " + MAX_LENGTH + " are allowed");
        }
        String rule = text.strip();
        try {
            return doParse(rule, classResolver, maxDuration);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid chaos rule [" + rule + "]: " + e.getMessage(), e.getCause());
        }
    }

    private static ChaosRule.Builder doParse(String text, ClassResolver classResolver, long maxDuration) {
        List<String[]> tokens = tokenize(text);

        ChaosRule.Builder b = new ChaosRule.Builder();
        b.text = text;
        Map<String, String> params = new LinkedHashMap<>();
        for (String[] token : tokens) {
            String name = token[0];
            String value = token[1];
            if (value == null) {
                Fault fault = fault(name);
                if (b.fault != null) {
                    throw new IllegalArgumentException(
                            "a rule has one fault, found " + b.fault.key + " and " + fault.key);
                }
                b.fault = fault;
                continue;
            }
            Target target = target(name);
            if (target != null) {
                if (b.target != null) {
                    throw new IllegalArgumentException(
                            "a rule has one target, found " + b.target.key + "= and " + target.key + "=");
                }
                b.target = target;
                b.pattern = value;
                continue;
            }
            if (params.put(name, value) != null) {
                throw new IllegalArgumentException("the parameter " + name + " is given twice");
            }
        }
        if (b.target == null) {
            throw new IllegalArgumentException(
                    "a rule needs a target: endpoint=<uri>, component=<name> or step=<id>");
        }
        if (b.fault == null) {
            throw new IllegalArgumentException("a rule needs a fault: error, delay or drop");
        }

        // the parameters of the fault
        switch (b.fault) {
            case ERROR -> errorParameters(b, params, classResolver);
            case DELAY -> {
                checkKeys(params, Set.of("duration"), b.fault);
                String duration = params.get("duration");
                if (duration == null) {
                    throw new IllegalArgumentException("the fault delay needs duration=<time>, such as duration=500ms");
                }
                b.duration = duration("duration", duration);
                if (b.duration > maxDuration) {
                    throw new IllegalArgumentException(
                            "duration=" + duration + " is longer than camel.chaos.maxDuration ("
                                                       + printTime(maxDuration) + ")");
                }
            }
            case DROP -> checkKeys(params, Set.of(), b.fault);
            default -> throw new IllegalStateException("Unknown fault " + b.fault);
        }

        hooks(b, params.get("at"));
        rate(b, params.get("rate"));
        until(b, params.get("until"), maxDuration);
        return b;
    }

    /**
     * Splits the rule into tokens: {@code name=value} (with a value that can be quoted) or a word. Each token is {name,
     * value}, or {word, null}.
     */
    private static List<String[]> tokenize(String text) {
        List<String[]> tokens = new ArrayList<>();
        int i = 0;
        int n = text.length();
        while (i < n) {
            char ch = text.charAt(i);
            if (ch == ' ' || ch == '\t') {
                i++;
                continue;
            }
            int start = i;
            while (i < n && text.charAt(i) != '=' && !isSpace(text.charAt(i))) {
                checkChar(text, i);
                if (text.charAt(i) == '"' || text.charAt(i) == '\'') {
                    throw new IllegalArgumentException(
                            "a quote can only start the value of a parameter (at position " + (i + 1) + ")");
                }
                i++;
            }
            String name = text.substring(start, i);
            if (i == n || isSpace(text.charAt(i))) {
                tokens.add(new String[] { name, null });
                continue;
            }
            // name=value
            if (name.isEmpty()) {
                throw new IllegalArgumentException("a parameter has no name before = (at position " + (i + 1) + ")");
            }
            checkName(name);
            i++;
            StringBuilder value = new StringBuilder();
            if (i < n && (text.charAt(i) == '"' || text.charAt(i) == '\'')) {
                char quote = text.charAt(i);
                int open = i;
                i++;
                boolean closed = false;
                while (i < n) {
                    char c = text.charAt(i);
                    checkChar(text, i);
                    if (c == '\\' && i + 1 < n && (text.charAt(i + 1) == quote || text.charAt(i + 1) == '\\')) {
                        value.append(text.charAt(i + 1));
                        i += 2;
                    } else if (c == quote) {
                        closed = true;
                        i++;
                        break;
                    } else {
                        value.append(c);
                        i++;
                    }
                }
                if (!closed) {
                    throw new IllegalArgumentException(
                            "the quote of " + name + " at position " + (open + 1) + " is not closed");
                }
                if (i < n && !isSpace(text.charAt(i))) {
                    throw new IllegalArgumentException(
                            "the quoted value of " + name + " is followed by text without a space (at position "
                                                       + (i + 1) + ")");
                }
            } else {
                while (i < n && !isSpace(text.charAt(i))) {
                    char c = text.charAt(i);
                    checkChar(text, i);
                    if (c == '"' || c == '\'') {
                        throw new IllegalArgumentException(
                                "a quote can only start the value of " + name + " (at position " + (i + 1) + ")");
                    }
                    value.append(c);
                    i++;
                }
            }
            if (value.isEmpty()) {
                throw new IllegalArgumentException("the parameter " + name + " has no value");
            }
            tokens.add(new String[] { name, value.toString() });
        }
        return tokens;
    }

    private static boolean isSpace(char ch) {
        return ch == ' ' || ch == '\t';
    }

    private static void checkChar(String text, int i) {
        char ch = text.charAt(i);
        if (ch < 0x20 && ch != '\t' || ch == 0x7f) {
            throw new IllegalArgumentException("a rule cannot contain control characters (at position " + (i + 1) + ")");
        }
    }

    private static void checkName(String name) {
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            boolean ok = ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9'
                    || ch == '_' || ch == '-' || ch == '.';
            if (!ok) {
                throw new IllegalArgumentException("the parameter name " + name + " contains the character '" + ch + "'");
            }
        }
    }

    private static Fault fault(String word) {
        return switch (word) {
            case "error" -> Fault.ERROR;
            case "delay" -> Fault.DELAY;
            case "drop" -> Fault.DROP;
            default -> throw new IllegalArgumentException(
                    "unknown fault " + word + " (the faults are error, delay and drop; a parameter is name=value)");
        };
    }

    private static Target target(String name) {
        for (Target target : Target.values()) {
            if (target.key.equals(name)) {
                return target;
            }
        }
        return null;
    }

    private static void checkKeys(Map<String, String> params, Set<String> faultKeys, Fault fault) {
        for (String key : params.keySet()) {
            if (GENERAL_KEYS.contains(key) || faultKeys.contains(key)) {
                continue;
            }
            StringJoiner known = new StringJoiner(", ");
            faultKeys.stream().sorted().forEach(known::add);
            GENERAL_KEYS.stream().sorted().forEach(known::add);
            throw new IllegalArgumentException(
                    "unknown parameter " + key + " for the fault " + fault.key + " (its parameters are " + known + ")");
        }
    }

    /** The parameters of {@code error}. */
    private static void errorParameters(ChaosRule.Builder b, Map<String, String> params, ClassResolver classResolver) {
        checkKeys(params, ERROR_KEYS, Fault.ERROR);
        b.message = params.get("message");
        String exception = params.get("exception");
        if (exception != null) {
            exceptionType(b, exception, classResolver);
        }
    }

    private static void exceptionType(ChaosRule.Builder b, String name, ClassResolver classResolver) {
        Class<?> type = classResolver.resolveClass(name);
        if (type == null) {
            throw new IllegalArgumentException("the exception class " + name + " is not found");
        }
        if (!Throwable.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("the exception class " + name + " is not a Throwable");
        }
        if (Modifier.isAbstract(type.getModifiers()) || !Modifier.isPublic(type.getModifiers())) {
            throw new IllegalArgumentException("the exception class " + name + " is abstract or not public");
        }
        @SuppressWarnings("unchecked")
        Class<? extends Throwable> throwable = (Class<? extends Throwable>) type;
        b.exceptionType = throwable;
        Constructor<? extends Throwable> constructor = constructor(throwable, String.class);
        b.exceptionTakesMessage = constructor != null;
        if (constructor == null) {
            constructor = constructor(throwable);
        }
        if (constructor == null) {
            throw new IllegalArgumentException(
                    "the exception class " + name
                                               + " has neither a public constructor taking a String message"
                                               + " nor a public no-argument one");
        }
        b.exceptionConstructor = constructor;
    }

    private static Constructor<? extends Throwable> constructor(Class<? extends Throwable> type, Class<?>... parameters) {
        try {
            return type.getConstructor(parameters);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * The hooks where the rule fires: the hooks of its target (only {@code at=} when given) that offer its fault.
     */
    private static void hooks(ChaosRule.Builder b, String at) {
        Map<Hook, Set<Fault>> offered = offered(b.target);
        if (at != null) {
            Hook hook = null;
            for (Hook h : Hook.values()) {
                if (h.key.equals(at)) {
                    hook = h;
                }
            }
            if (hook == null) {
                throw new IllegalArgumentException("unknown at=" + at + " (the hooks are send and step)");
            }
            if (!offered.containsKey(hook)) {
                throw new IllegalArgumentException(
                        "at=" + at + " is not a hook of the target " + b.target.key + "= ("
                                                   + describe(b.target, offered) + ")");
            }
            b.at = hook;
        }
        Set<Hook> hooks = EnumSet.noneOf(Hook.class);
        for (Map.Entry<Hook, Set<Fault>> entry : offered.entrySet()) {
            if ((b.at == null || b.at == entry.getKey()) && entry.getValue().contains(b.fault)) {
                hooks.add(entry.getKey());
            }
        }
        if (hooks.isEmpty()) {
            throw new IllegalArgumentException(
                    "the fault " + b.fault.key + " is not offered " + (b.at != null ? "at=" + b.at.key : "at any hook")
                                               + " of the target " + b.target.key + "= (" + describe(b.target, offered)
                                               + ")");
        }
        b.hooks = hooks;
    }

    /** The hooks of a target and the faults each offers. */
    static Map<Hook, Set<Fault>> offered(Target target) {
        Map<Hook, Set<Fault>> answer = new LinkedHashMap<>();
        switch (target) {
            case ENDPOINT, COMPONENT -> answer.put(Hook.SEND, EnumSet.of(Fault.ERROR, Fault.DELAY, Fault.DROP));
            case STEP -> answer.put(Hook.STEP, EnumSet.of(Fault.ERROR, Fault.DELAY));
            default -> throw new IllegalStateException("Unknown target " + target);
        }
        return answer;
    }

    private static String describe(Target target, Map<Hook, Set<Fault>> offered) {
        StringJoiner sj = new StringJoiner("; ");
        for (Map.Entry<Hook, Set<Fault>> entry : offered.entrySet()) {
            StringJoiner faults = new StringJoiner(", ");
            entry.getValue().forEach(f -> faults.add(f.key));
            sj.add("at=" + entry.getKey().key + " offers " + faults);
        }
        return "the hooks of " + target.key + "= are: " + sj;
    }

    private static void rate(ChaosRule.Builder b, String rate) {
        if (rate == null) {
            return;
        }
        if (rate.startsWith("every:")) {
            b.every = number("rate", rate.substring(6), 9);
            if (b.every < 1) {
                throw new IllegalArgumentException("rate=" + rate + " needs a number of 1 or more after every:");
            }
            return;
        }
        // a decimal number such as 0.3 or 1 (no sign, exponent, NaN or infinity)
        int dots = 0;
        for (int i = 0; i < rate.length(); i++) {
            char ch = rate.charAt(i);
            if (ch == '.') {
                dots++;
            } else if (ch < '0' || ch > '9') {
                dots = 2;
            }
        }
        if (dots > 1 || rate.length() > 12 || rate.equals(".")) {
            throw new IllegalArgumentException(
                    "rate=" + rate + " is neither a probability (such as 0.3) nor every:<n> (such as every:5)");
        }
        double probability = Double.parseDouble(rate);
        if (probability <= 0 || probability > 1) {
            throw new IllegalArgumentException("rate=" + rate + " is not a probability above 0 and at most 1");
        }
        b.probability = probability;
    }

    private static void until(ChaosRule.Builder b, String until, long maxDuration) {
        if (until == null) {
            return;
        }
        if (isDigits(until)) {
            b.untilCount = number("until", until, 18);
            if (b.untilCount < 1) {
                throw new IllegalArgumentException("until=" + until + " must be a count of 1 or more, or a duration");
            }
            return;
        }
        b.untilDuration = duration("until", until);
        if (b.untilDuration > maxDuration) {
            throw new IllegalArgumentException(
                    "until=" + until + " is longer than camel.chaos.maxDuration ("
                                               + printTime(maxDuration) + ")");
        }
    }

    private static boolean isDigits(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return true;
    }

    private static long number(String name, String text, int maxDigits) {
        if (!isDigits(text) || text.length() > maxDigits) {
            throw new IllegalArgumentException(name + "=" + text + " needs a whole number of at most " + maxDigits + " digits");
        }
        return Long.parseLong(text);
    }

    /** A time such as 500ms, 2s, 1m or 1h30m; a number without a unit is in milliseconds. */
    private static long duration(String name, String text) {
        long millis = -1;
        if (text.length() <= 32) {
            boolean ok = true;
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (!(ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z')) {
                    ok = false;
                }
            }
            if (ok) {
                try {
                    millis = TimeUtils.toMilliSeconds(text.toLowerCase(Locale.ENGLISH));
                } catch (IllegalArgumentException e) {
                    millis = -1;
                }
            }
        }
        if (millis < 0) {
            throw new IllegalArgumentException(name + "=" + text + " is not a time, such as 500ms, 2s or 1m");
        }
        if (millis == 0) {
            throw new IllegalArgumentException(name + "=" + text + " must be longer than 0");
        }
        return millis;
    }

    /** A time for a person: 30s, 1m0s, 1h0m0s, or 1500ms when it is not whole seconds. */
    static String printTime(long millis) {
        return TimeUtils.printDuration(millis, millis % 1000 != 0);
    }
}
