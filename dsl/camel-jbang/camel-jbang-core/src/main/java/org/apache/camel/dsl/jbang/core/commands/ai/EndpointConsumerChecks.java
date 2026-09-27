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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.dsl.yaml.validator.EndpointConsumers;

/**
 * A {@code direct:} or {@code seda:} endpoint a YAML route sends to, and no route of the application consumes
 * (CAMEL-24955). The routes of an application are spread over the files of its directory, so the endpoints the other
 * route files consume - YAML, Java and XML - are read from them and handed to the check.
 */
public final class EndpointConsumerChecks {

    /** A Java DSL route input: from( not called on something else, so not Instant.from( or List.from(. */
    private static final Pattern JAVA_FROM = Pattern.compile("(?<![.\\w])from\\s*\\(\\s*(\"([^\"]*)\")?");
    private static final Pattern XML_FROM = Pattern.compile("<from\\s[^>]*?\\buri\\s*=\\s*[\"']([^\"']*)[\"']");

    private EndpointConsumerChecks() {
    }

    /**
     * @param  content     the YAML route file
     * @param  directory   the directory of the application's route files; null says nothing
     * @param  excludeFile the file being validated, whose routes come from the content
     * @return             the messages, one per endpoint no route consumes
     */
    public static List<String> validateYamlConsumers(String content, Path directory, String excludeFile) {
        return EndpointConsumers.check(content, consumed(directory, excludeFile));
    }

    /**
     * The {@code direct:} and {@code seda:} endpoints the route files of the directory consume, leaving out the file
     * being validated; null when they cannot be known: no directory, or a route input the scan cannot read (a Java
     * {@code from(} with no literal, a placeholder), which could be any endpoint.
     */
    static Set<String> consumed(Path directory, String excludeFile) {
        if (directory == null || !Files.isDirectory(directory)) {
            return null;
        }
        Set<String> answer = new HashSet<>();
        try (var stream = Files.list(directory)) {
            for (Path p : stream.filter(Files::isRegularFile).toList()) {
                String fn = p.getFileName().toString();
                if (fn.equals(excludeFile)) {
                    continue;
                }
                String lower = fn.toLowerCase(Locale.ROOT);
                Set<String> found;
                if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
                    found = EndpointConsumers.consumed(Files.readString(p));
                } else if (lower.endsWith(".java")) {
                    found = javaConsumed(Files.readString(p));
                } else if (lower.endsWith(".xml")) {
                    found = xmlConsumed(Files.readString(p));
                } else {
                    continue;
                }
                if (found == null) {
                    return null;
                }
                answer.addAll(found);
            }
        } catch (IOException e) {
            // an unreadable directory or file: what it consumes is not known
            return null;
        }
        return answer;
    }

    static Set<String> javaConsumed(String src) {
        Set<String> answer = new HashSet<>();
        Matcher m = JAVA_FROM.matcher(src);
        while (m.find()) {
            // from(someConstant) or from(direct("x")): the endpoint is not in the source as a literal
            if (m.group(2) == null || !add(m.group(2), answer)) {
                return null;
            }
        }
        return answer;
    }

    static Set<String> xmlConsumed(String src) {
        Set<String> answer = new HashSet<>();
        Matcher m = XML_FROM.matcher(src);
        while (m.find()) {
            if (!add(m.group(1), answer)) {
                return null;
            }
        }
        return answer;
    }

    /** Adds the endpoint when it is a direct: or seda: one; false when it is only known at runtime. */
    private static boolean add(String uri, Set<String> answer) {
        if (EndpointConsumers.isDynamic(uri)) {
            return false;
        }
        String endpoint = EndpointConsumers.endpoint(uri);
        if (endpoint != null) {
            answer.add(endpoint);
        }
        return true;
    }
}
