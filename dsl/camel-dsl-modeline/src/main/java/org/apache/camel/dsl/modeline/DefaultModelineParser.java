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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.Resource;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.StringQuoteHelper;

public class DefaultModelineParser implements ModelineParser {

    public static final String MODELINE_START = "camel-k:";
    public static final String JBANG_DEPS_START = "//DEPS";

    private final Map<String, Trait> traits = new HashMap<>();

    public DefaultModelineParser() {
        // add known traits
        Trait trait = new DependencyTrait();
        this.traits.put(trait.getName(), trait);
        // property trait is added from the default mode line factory
        trait = new NameTrait();
        this.traits.put(trait.getName(), trait);
        trait = new EnvTrait();
        this.traits.put(trait.getName(), trait);
    }

    @Override
    public void addTrait(Trait trait) {
        this.traits.put(trait.getName(), trait);
    }

    @Override
    public List<CamelContextCustomizer> parse(Resource resource) throws Exception {
        List<CamelContextCustomizer> answer = new ArrayList<>();

        if (resource.exists()) {
            try (LineNumberReader reader = new LineNumberReader(resource.getReader())) {
                String line = reader.readLine();
                while (line != null) {
                    List<CamelContextCustomizer> list = parse(resource, line);
                    answer.addAll(list);
                    line = reader.readLine();
                }
            }
        }

        return answer;
    }

    protected List<CamelContextCustomizer> parse(Resource resource, String line) {
        if (!isModeline(line)) {
            return Collections.emptyList();
        }
        line = removeLeadingComments(line);

        List<CamelContextCustomizer> answer = new ArrayList<>();

        if (line.startsWith(MODELINE_START)) {
            line = line.substring(MODELINE_START.length()).trim();
            // split into key value pairs
            String[] parts = StringQuoteHelper.splitSafeQuote(line, ' ', false);
            for (String part : parts) {
                part = part.trim();
                String key = StringHelper.before(part, "=");
                String value = StringHelper.after(part, "=");
                Trait trait = parseModeline(resource, key, value);
                if (trait != null) {
                    CamelContextCustomizer customizer = trait.parseTrait(resource, value);
                    if (customizer != null) {
                        answer.add(customizer);
                    }
                }
            }
        }

        if (line.startsWith(JBANG_DEPS_START)) {
            line = line.substring(JBANG_DEPS_START.length()).trim();
            line = line.trim();
            Trait dep = traits.get("dependency");
            String[] parts = StringQuoteHelper.splitSafeQuote(line, ' ', false);
            for (String part : parts) {
                part = part.trim();
                if (part.endsWith("@pom")) {
                    // skip @pom
                    continue;
                }
                CamelContextCustomizer customizer = dep.parseTrait(resource, part);
                if (customizer != null) {
                    answer.add(customizer);
                }
            }
        }

        return answer;
    }

    @Override
    public Trait parseModeline(Resource resource, String key, String value) {
        return traits.get(key);
    }

    @Override
    public boolean isModeline(String line) {
        // the line must be a comment and start with camel-k
        if (line == null) {
            return false;
        }
        line = removeLeadingComments(line);
        return line.startsWith(MODELINE_START) || line.startsWith(JBANG_DEPS_START);
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
