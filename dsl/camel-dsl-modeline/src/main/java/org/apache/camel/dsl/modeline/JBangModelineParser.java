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

package org.apache.camel.dsl.modeline;

import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.DependencyStrategy;
import org.apache.camel.spi.Resource;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.StringQuoteHelper;

public class JBangModelineParser implements ModelineParser {

    public static final String JBANG_DEPS_START = "//DEPS";

    @Override
    public List<CamelContextCustomizer> parse(Resource resource) throws Exception {
        List<CamelContextCustomizer> answer = new ArrayList<>();

        if (resource.exists()) {
            try (LineNumberReader reader = new LineNumberReader(resource.getReader())) {
                String line = reader.readLine();
                while (line != null) {
                    List<CamelContextCustomizer> list = parse(line);
                    answer.addAll(list);
                    line = reader.readLine();
                }
            }
        }

        return answer;
    }

    @Override
    public boolean isModeline(String line) {
        // the line must be a comment and start with //DEPS
        if (line == null) {
            return false;
        }
        line = removeLeadingComments(line);
        return line.startsWith(JBANG_DEPS_START);
    }

    protected List<CamelContextCustomizer> parse(String line) {
        if (!isModeline(line)) {
            return Collections.emptyList();
        }
        line = removeLeadingComments(line);

        List<CamelContextCustomizer> answer = new ArrayList<>();

        if (line.startsWith(JBANG_DEPS_START)) {
            line = line.substring(JBANG_DEPS_START.length()).trim();
            line = line.trim();
            String[] parts = StringQuoteHelper.splitSafeQuote(line, ' ', false);
            for (String part : parts) {
                part = part.trim();
                if (part.endsWith("@pom")) {
                    // skip @pom
                    continue;
                }
                // in case DEPS uses jbang ${ } style that refer to JVM system properties
                if (part.contains("${") && part.contains("}")) {
                    String target = StringHelper.between(part, "${", "}");
                    String value = StringHelper.before(target, ":", target);
                    if (target.contains(":")) {
                        String def = StringHelper.after(target, ":");
                        value = System.getProperty(value, def);
                    } else {
                        String found = System.getProperty(value);
                        if (found == null) {
                            throw new IllegalArgumentException(
                                    "Cannot find JVM system property: " + value + " for dependency: " + part);
                        }
                        value = found;
                    }
                    part = part.replace("${" + target + "}", value);
                }
                final String dep = part;
                CamelContextCustomizer customizer = camelContext -> {
                    for (DependencyStrategy ds : camelContext.getRegistry().findByType(DependencyStrategy.class)) {
                        ds.onDependency(dep);
                    }
                };
                answer.add(customizer);
            }
        }

        return answer;
    }

    private static String removeLeadingComments(String line) {
        if (line == null) {
            return null;
        }

        line = line.trim();
        while (!line.startsWith(JBANG_DEPS_START) && line.startsWith("/") || line.startsWith("#")) {
            line = line.substring(1);
        }

        line = line.trim();
        return line;
    }
}
