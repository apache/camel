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

import com.datasonnet.document.MediaTypes;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.dataweave.DataWeaveConversionException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.skyscreamer.jsonassert.JSONAssert;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Executed test corpus for DataWeave-to-DataSonnet auto-conversion (CAMEL-25323).
 * <p>
 * Each test case loads a {@code .dwl} resource from {@code corpus/}, sends a real input message through a Camel route
 * backed by the DataSonnet language (which auto-converts the DataWeave), and asserts the output against an expected
 * JSON payload. This ensures that the generated DataSonnet is not only syntactically plausible but actually runs and
 * produces correct results.
 * <p>
 * A separate test verifies that routes referencing DataWeave scripts containing unsupported constructs fail at
 * expression creation time with a clear error rather than silently returning {@code null}.
 */
class DataWeaveExecutedCorpusTest extends CamelTestSupport {

    // -------------------------------------------------------------------------
    // Corpus cases — each sends a JSON body through a .dwl resource and checks
    // the output JSON.
    // -------------------------------------------------------------------------

    @Test
    void testFieldAccess() throws Exception {
        template.sendBody("direct:fieldAccess", "{\"name\":\"Widget\",\"qty\":3,\"price\":5}");
        MockEndpoint mock = getMockEndpoint("mock:fieldAccess");
        mock.assertExchangeReceived(0);
        String result = mock.getExchanges().get(0).getMessage().getBody(String.class);
        JSONAssert.assertEquals("{\"name\":\"Widget\",\"qty\":3,\"total\":15}", result, true);
    }

    @Test
    void testStringConcat() throws Exception {
        template.sendBody("direct:stringConcat", "{\"firstName\":\"John\",\"lastName\":\"Doe\"}");
        MockEndpoint mock = getMockEndpoint("mock:stringConcat");
        mock.assertExchangeReceived(0);
        String result = mock.getExchanges().get(0).getMessage().getBody(String.class);
        JSONAssert.assertEquals("{\"greeting\":\"Hello, John Doe\"}", result, true);
    }

    /**
     * Verifies the if/else branch coverage: score ≥ 90 → A, ≥ 80 → B, otherwise → C. A single shared {@code ifElse.dwl}
     * script is reused across all three threshold cases.
     */
    @ParameterizedTest
    @CsvSource({
            "95, A",
            "85, B",
            "70, C"
    })
    void testIfElse(int score, String expectedLabel) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:ifElse");
        mock.reset();
        template.sendBody("direct:ifElse", "{\"score\":" + score + "}");
        mock.assertExchangeReceived(0);
        String result = mock.getExchanges().get(0).getMessage().getBody(String.class);
        JSONAssert.assertEquals("{\"label\":\"" + expectedLabel + "\"}", result, true);
    }

    @Test
    void testMapTransform() throws Exception {
        template.sendBody("direct:mapTransform",
                "{\"items\":[{\"name\":\"A\",\"price\":10},{\"name\":\"B\",\"price\":20}]}");
        MockEndpoint mock = getMockEndpoint("mock:mapTransform");
        mock.assertExchangeReceived(0);
        String result = mock.getExchanges().get(0).getMessage().getBody(String.class);
        JSONAssert.assertEquals("[{\"name\":\"A\",\"price\":10},{\"name\":\"B\",\"price\":20}]", result, true);
    }

    @Test
    void testFilter() throws Exception {
        template.sendBody("direct:filter",
                "{\"items\":[{\"name\":\"A\",\"qty\":1},{\"name\":\"B\",\"qty\":3},{\"name\":\"C\",\"qty\":2}]}");
        MockEndpoint mock = getMockEndpoint("mock:filter");
        mock.assertExchangeReceived(0);
        String result = mock.getExchanges().get(0).getMessage().getBody(String.class);
        JSONAssert.assertEquals("[{\"name\":\"B\",\"qty\":3},{\"name\":\"C\",\"qty\":2}]", result, true);
    }

    @Test
    void testReduce() throws Exception {
        template.sendBody("direct:reduce", "{\"amounts\":[1,2,3,4]}");
        MockEndpoint mock = getMockEndpoint("mock:reduce");
        mock.assertExchangeReceived(0);
        String result = mock.getExchanges().get(0).getMessage().getBody(String.class);
        JSONAssert.assertEquals("{\"total\":10}", result, true);
    }

    @Test
    void testBuiltinFunctions() throws Exception {
        template.sendBody("direct:builtinFunctions", "{\"text\":\"Hello World\"}");
        MockEndpoint mock = getMockEndpoint("mock:builtinFunctions");
        mock.assertExchangeReceived(0);
        String result = mock.getExchanges().get(0).getMessage().getBody(String.class);
        JSONAssert.assertEquals("{\"upper\":\"HELLO WORLD\",\"lower\":\"hello world\",\"len\":11}", result, true);
    }

    @Test
    void testTypeCoercion() throws Exception {
        template.sendBody("direct:typeCoercion", "{\"amount\":\"42\"}");
        MockEndpoint mock = getMockEndpoint("mock:typeCoercion");
        mock.assertExchangeReceived(0);
        String result = mock.getExchanges().get(0).getMessage().getBody(String.class);
        JSONAssert.assertEquals("{\"amount\":42,\"label\":\"42\"}", result, true);
    }

    /**
     * The $ / $$ shorthand inside object constructors, function calls, unary minus, attribute and existence selectors,
     * where $$ is the index in map and the accumulator in reduce.
     */
    @Test
    void testShorthand() throws Exception {
        template.sendBody("direct:shorthand", "{\"items\":["
                                              + "{\"@sku\":\"A1\",\"name\":\"apple\",\"price\":3,\"qty\":2},"
                                              + "{\"@sku\":\"B2\",\"name\":\"pear\",\"price\":5,\"qty\":1,\"discount\":true},"
                                              + "{\"@sku\":\"A1\",\"name\":\"apple\",\"price\":3,\"qty\":4}]}");
        MockEndpoint mock = getMockEndpoint("mock:shorthand");
        mock.assertExchangeReceived(0);
        String result = mock.getExchanges().get(0).getMessage().getBody(String.class);
        JSONAssert.assertEquals("{"
                                + "\"names\":[\"APPLE\",\"PEAR\",\"APPLE\"],"
                                + "\"lines\":[{\"line\":0,\"sku\":\"A1\"},{\"line\":1,\"sku\":\"B2\"},{\"line\":2,\"sku\":\"A1\"}],"
                                + "\"byPriceDesc\":[\"pear\",\"apple\",\"apple\"],"
                                + "\"discounted\":[\"pear\"],"
                                + "\"skus\":[\"A1\",\"B2\"],"
                                + "\"totalQty\":7}",
                result, true);
    }

    // -------------------------------------------------------------------------
    // Fail-fast test — unsupported construct must throw at expression creation time
    // -------------------------------------------------------------------------

    @Test
    void testUnsupportedConstructFailsFast() {
        // The DataWeave update operator is not auto-convertible; DatasonnetLanguage.convertDataWeave()
        // must reject the expression with a clear DataWeaveConversionException rather than
        // silently emitting null and letting the route start.
        String withUpdate = "%dw 2.0\noutput application/json\n---\npayload update { case .name -> upper($) }";
        assertThrows(DataWeaveConversionException.class,
                () -> context.resolveLanguage("datasonnet")
                        .createExpression(withUpdate, new Object[] {
                                String.class, null,
                                MediaTypes.APPLICATION_JSON_VALUE, MediaTypes.APPLICATION_JSON_VALUE }));
    }

    // -------------------------------------------------------------------------
    // Strict-parser test — malformed DataWeave must report error at creation time
    // -------------------------------------------------------------------------

    @Test
    void testStrictParserRejectsMalformedScript() {
        // Inline DataWeave with a missing closing brace — the parser's strict expect()
        // must propagate as an exception from DatasonnetLanguage.convertDataWeave()
        // rather than silently producing a garbled AST.
        String malformed = "%dw 2.0\noutput application/json\n---\n{ name: payload.name";
        assertThrows(DataWeaveConversionException.class,
                () -> context.resolveLanguage("datasonnet")
                        .createExpression(malformed, new Object[] {
                                String.class, null,
                                MediaTypes.APPLICATION_JSON_VALUE, MediaTypes.APPLICATION_JSON_VALUE }));
    }

    // -------------------------------------------------------------------------
    // Route definitions
    // -------------------------------------------------------------------------

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:fieldAccess")
                        .transform(datasonnet("resource:classpath:corpus/fieldAccess.dwl", String.class,
                                MediaTypes.APPLICATION_JSON_VALUE, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:fieldAccess");

                from("direct:stringConcat")
                        .transform(datasonnet("resource:classpath:corpus/stringConcat.dwl", String.class,
                                MediaTypes.APPLICATION_JSON_VALUE, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:stringConcat");

                // Single route shared by all three ifElse threshold cases (score >= 90 → A, >= 80 → B, else → C).
                // The mock is reset before each @ParameterizedTest iteration to keep assertions independent.
                from("direct:ifElse")
                        .transform(datasonnet("resource:classpath:corpus/ifElse.dwl", String.class,
                                MediaTypes.APPLICATION_JSON_VALUE, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:ifElse");

                from("direct:mapTransform")
                        .transform(datasonnet("resource:classpath:corpus/mapTransform.dwl", String.class,
                                MediaTypes.APPLICATION_JSON_VALUE, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:mapTransform");

                from("direct:filter")
                        .transform(datasonnet("resource:classpath:corpus/filter.dwl", String.class,
                                MediaTypes.APPLICATION_JSON_VALUE, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:filter");

                from("direct:reduce")
                        .transform(datasonnet("resource:classpath:corpus/reduce.dwl", String.class,
                                MediaTypes.APPLICATION_JSON_VALUE, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:reduce");

                from("direct:builtinFunctions")
                        .transform(datasonnet("resource:classpath:corpus/builtinFunctions.dwl", String.class,
                                MediaTypes.APPLICATION_JSON_VALUE, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:builtinFunctions");

                from("direct:typeCoercion")
                        .transform(datasonnet("resource:classpath:corpus/typeCoercion.dwl", String.class,
                                MediaTypes.APPLICATION_JSON_VALUE, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:typeCoercion");

                from("direct:shorthand")
                        .transform(datasonnet("resource:classpath:corpus/shorthand.dwl", String.class,
                                MediaTypes.APPLICATION_JSON_VALUE, MediaTypes.APPLICATION_JSON_VALUE))
                        .to("mock:shorthand");
            }
        };
    }
}
