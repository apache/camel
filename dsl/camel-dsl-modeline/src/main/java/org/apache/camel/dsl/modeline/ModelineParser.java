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

import org.apache.camel.CamelContext;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.Resource;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.StringQuoteHelper;

public class ModelineParser {

    public static final String MODELINE_START = "camel-k:";

    private final CamelContext camelContext;
    private final Map<String, Trait> traits = new HashMap<>();

    public ModelineParser(CamelContext camelContext) {
        this.camelContext = camelContext;

        // add known traits
        Trait trait = new DependencyTrait();
        this.traits.put(trait.getName(), trait);
        // property trait is added from the default mode line factory
        trait = new NameTrait();
        this.traits.put(trait.getName(), trait);
        trait = new EnvTrait();
        this.traits.put(trait.getName(), trait);
    }

    public void addTrait(Trait trait) {
        this.traits.put(trait.getName(), trait);
    }

    public List<CamelContextCustomizer> parse(Resource resource) throws Exception {
        List<CamelContextCustomizer> answer = new ArrayList<>();

        try (LineNumberReader reader = new LineNumberReader(resource.getReader())) {
            String line = reader.readLine();
            while (line != null) {
                List<CamelContextCustomizer> list = parse(resource, line);
                answer.addAll(list);
                line = reader.readLine();
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
                String name = StringHelper.before(part, "=");
                String value = StringHelper.after(part, "=");
                Trait trait = traits.get(name);
                if (trait != null) {
                    CamelContextCustomizer customizer = trait.parseTrait(resource, value);
                    if (customizer != null) {
                        answer.add(customizer);
                    }
                }
            }
        }

        return answer;
    }

    private static boolean isModeline(String line) {
        // the line must be a comment and start with camel-k
        if (line == null) {
            return false;
        }
        line = removeLeadingComments(line);
        return line.startsWith(MODELINE_START);
    }

    private static String removeLeadingComments(String line) {
        if (line == null) {
            return null;
        }

        line = line.trim();
        while (line.startsWith("/") || line.startsWith("#")) {
            line = line.substring(1);
        }

        line = line.trim();
        return line;
    }

}
