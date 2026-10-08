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
package org.apache.camel.language.datasonnet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.component.dataweave.DataWeaveConversionException;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.skyscreamer.jsonassert.JSONAssert;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Runs the DataWeave snippets in {@code dataweave-corpus/corpus.txt} through the DataWeave to DataSonnet conversion of
 * the datasonnet language and checks the output against what DataWeave produces.
 * <p>
 * Every snippet must either produce the same output as DataWeave, or (when marked {@code @unsupported}) fail at
 * conversion. A conversion that succeeds but produces a different output, or fails when the message is processed, is
 * never acceptable: the route would start and silently misbehave.
 */
class DataWeaveCorpusTest extends CamelTestSupport {

    // DataWeave formats numbers and dates in the default locale; the corpus has the output of an English locale
    private static Locale defaultLocale;

    @BeforeAll
    static void englishLocale() {
        defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterAll
    static void restoreLocale() {
        Locale.setDefault(defaultLocale);
    }

    private static final String JSON = "application/json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    record Entry(String id, String title, Map<String, String> directives, String script) {
        String directive(String name, String defaultValue) {
            return directives.getOrDefault(name, defaultValue);
        }

        @Override
        public String toString() {
            return id + " " + title;
        }
    }

    static Stream<Arguments> corpus() throws IOException {
        String defaultBody = resource("dataweave-corpus/default.json");
        List<Arguments> answer = new ArrayList<>();
        String id = null;
        String title = null;
        Map<String, String> directives = new LinkedHashMap<>();
        StringBuilder script = new StringBuilder();
        for (String line : resource("dataweave-corpus/corpus.txt").split("\n", -1)) {
            if (id == null && line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("=== ")) {
                if (id != null) {
                    answer.add(Arguments.of(new Entry(id, title, directives, script.toString().strip())));
                }
                String header = line.substring(4);
                int space = header.indexOf(' ');
                id = space > 0 ? header.substring(0, space) : header;
                title = space > 0 ? header.substring(space + 1) : "";
                directives = new LinkedHashMap<>();
                directives.put("in", defaultBody);
                script = new StringBuilder();
            } else if (line.startsWith("@") && script.isEmpty()) {
                int space = line.indexOf(' ');
                directives.put(line.substring(1, space > 0 ? space : line.length()),
                        space > 0 ? line.substring(space + 1) : "");
            } else if (id != null) {
                script.append(line).append('\n');
            }
        }
        if (id != null) {
            answer.add(Arguments.of(new Entry(id, title, directives, script.toString().strip())));
        }
        return answer.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void testCorpus(Entry entry) throws Exception {
        Properties properties = new Properties();
        properties.put("my.prop", "x");
        context.getPropertiesComponent().setOverrideProperties(properties);

        String script = entry.script();
        if (!script.startsWith("%dw")) {
            // a header ends with --- on a line of its own
            boolean header = script.lines().anyMatch("---"::equals);
            script = (header ? "%dw 2.0\n" : "%dw 2.0\noutput application/json\n---\n") + script;
        }
        String expected = entry.directive("exp", "?");
        String unsupported = entry.directives().get("unsupported");
        String dataWeave = script;

        if ("PARSE-ERROR".equals(expected) || unsupported != null) {
            assertThrows(DataWeaveConversionException.class, () -> createExpression(entry, dataWeave),
                    unsupported != null
                            ? "Marked @unsupported (" + unsupported + ") but converted: remove the marker if it now works"
                            : "Invalid DataWeave must fail at conversion");
            return;
        }

        Expression expression = createExpression(entry, dataWeave);
        Exchange exchange = new DefaultExchange(context);
        String body = entry.directive("in", null);
        if (!JSON.equals(entry.directive("inType", JSON))) {
            body = body.replace("\\n", "\n");
        }
        exchange.getMessage().setBody(body);
        readMap(entry.directives().get("headers")).forEach(exchange.getMessage()::setHeader);
        readMap(entry.directives().get("vars")).forEach(exchange::setVariable);

        String actual;
        try {
            actual = expression.evaluate(exchange, String.class);
        } catch (Exception e) {
            fail("Converted without error but fails when evaluated: " + e.getMessage(), e);
            return;
        }
        if (exchange.getException() != null) {
            fail("Converted without error but fails when evaluated: " + exchange.getException().getMessage(),
                    exchange.getException());
        }
        if (!"?".equals(expected)) {
            JSONAssert.assertEquals("{\"v\":" + expected + "}", "{\"v\":" + actual + "}", true);
        }
    }

    private Expression createExpression(Entry entry, String dataWeave) {
        return context.resolveLanguage("datasonnet").createExpression(dataWeave,
                new Object[] {
                        String.class, null, entry.directive("inType", JSON), entry.directive("outType", JSON) });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readMap(String json) throws IOException {
        return json == null ? Map.of() : MAPPER.readValue(json, Map.class);
    }

    private static String resource(String path) throws IOException {
        try (InputStream is = DataWeaveCorpusTest.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Resource not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
