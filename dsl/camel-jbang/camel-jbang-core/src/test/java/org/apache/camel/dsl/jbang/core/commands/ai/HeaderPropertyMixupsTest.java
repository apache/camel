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

import java.util.List;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25516: a header, exchange property or variable read where the route keeps the name in another of them.
 */
class HeaderPropertyMixupsTest {

    private static String route(String from, String set, String read) {
        return "- route:\n    from:\n      uri: " + from + "\n      steps:\n"
               + "        - " + set + ":\n            name: sku\n            expression:\n              simple: ${body[sku]}\n"
               + "        - toD:\n            uri: \"http://localhost:8080/stock/" + read + "\"\n";
    }

    @Test
    void aHeaderReadWhereTheRouteKeepsAProperty() {
        List<String> msgs = HeaderPropertyMixups.validate(route("file:orders", "setProperty", "${header.sku}"));

        assertThat(msgs).singleElement().isEqualTo("Line 10: ${header.sku} reads a header sku, but the route keeps sku in"
                                                   + " an exchange property (the header is null here): write"
                                                   + " ${exchangeProperty.sku}, or keep it in a variable instead"
                                                   + " (setVariable, read with ${variable.sku}), which is what Camel"
                                                   + " uses for a value of the route");
    }

    @Test
    void aPropertyReadWhereTheRouteKeepsAHeader() {
        assertThat(HeaderPropertyMixups.validate(route("timer:tick", "setHeader", "${exchangeProperty.sku}")))
                .singleElement().asString()
                .startsWith("Line 10: ${exchangeProperty.sku} reads an exchange property sku, but the route keeps sku in"
                            + " a header")
                .endsWith("write ${header.sku}");
    }

    @Test
    void variablesAreTheThirdPlace() {
        assertThat(HeaderPropertyMixups.validate(route("file:orders", "setVariable", "${header.sku}")))
                .singleElement().asString()
                .startsWith("Line 10: ${header.sku} reads a header sku, but the route keeps sku in a variable")
                .endsWith("write ${variable.sku}");
        // r24 run-order-generator step 1: a variable read, an exchange property set
        assertThat(HeaderPropertyMixups.validate(route("file:orders", "setProperty", "${variable.sku}")))
                .singleElement().asString().contains("write ${exchangeProperty.sku}")
                .endsWith("or set it with setVariable instead of setProperty, which is what Camel uses for a value of"
                          + " the route");
        assertThat(HeaderPropertyMixups.validate(route("file:orders", "setVariable", "${variable.sku}"))).isEmpty();
    }

    @Test
    void theRightReadIsNotReported() {
        assertThat(HeaderPropertyMixups.validate(route("file:orders", "setProperty", "${exchangeProperty.sku}"))).isEmpty();
        assertThat(HeaderPropertyMixups.validate(route("file:orders", "setHeader", "${header.sku}"))).isEmpty();
    }

    @Test
    void quietWhereTheNameCanComeFromElsewhere() {
        // a caller, or an HTTP request, can carry the header
        assertThat(HeaderPropertyMixups.validate(route("direct:check", "setProperty", "${header.sku}"))).isEmpty();
        assertThat(HeaderPropertyMixups.validate(route("platform-http:/check", "setProperty", "${header.sku}")))
                .isEmpty();
        // a path parameter is a header of the request
        assertThat(HeaderPropertyMixups.validate(route("file:orders", "setProperty", "${header.sku}")
                                                 + "- rest:\n    get:\n      - path: /stock/{sku}\n        to: direct:x\n"))
                .isEmpty();
        // set in both forms
        String both = route("file:orders", "setProperty", "${header.sku}")
                .replace("        - toD:",
                        "        - setHeader:\n            name: sku\n            constant: x\n        - toD:");
        assertThat(HeaderPropertyMixups.validate(both)).isEmpty();
    }

    @Test
    void theValidatorOfAWriteReportsIt() {
        // the file is unmarshalled first, or the body type check reports the read of ${body[sku]} on a GenericFile;
        // and the lookup is a GET, or it reports the parsed order sent with POST
        String route = route("file:orders", "setProperty", "${header.sku}")
                .replace("      steps:\n", "      steps:\n        - unmarshal:\n            json: {}\n")
                .replace("${header.sku}\"", "${header.sku}?httpMethod=GET\"");
        List<String> msgs = SourceValidator.validate("http-client.camel.yaml", route, new DefaultCamelCatalog(), null);

        assertThat(msgs).anyMatch(m -> m.contains("${header.sku} reads a header sku"));
    }

    /** The order generator with the set step given, and a log of ${property.orderId}, which Camel does not have. */
    private static String generator(String set) {
        return """
                - route:
                    id: order-generator
                    from:
                      uri: timer
                      parameters:
                        timerName: orders
                        period: 5000
                      steps:
                """ + set.indent(8) + """
                        - log:
                            message: "New order ${property.orderId}"
                """;
    }

    private static String propertyFunction(String content) {
        return SourceValidator.validate("order-generator.camel.yaml", content, new DefaultCamelCatalog(), null).stream()
                .filter(m -> m.contains("Unknown function: property.orderId"))
                .findFirst().orElseThrow();
    }

    @Test
    void thePropertyFunctionSaysWhereTheRouteKeepsTheValue() {
        // r25 run-order-generator step 1, asked to set a variable: setProperty (with propertyName:, or the name as the
        // key) and ${property.orderId}; the hint ${exchangeProperty.orderId} was followed, and the step failed
        String setProperty = "- setProperty:\n    propertyName: orderId\n    constant: ORD-1001\n";
        String msg = propertyFunction(generator(setProperty));
        assertThat(msg).isEqualTo("Line 13: Simple syntax error: Unknown function: property.orderId (did you mean"
                                  + " ${exchangeProperty.orderId}?), which reads the exchange property that setProperty"
                                  + " sets; or keep orderId in a variable instead, which is what Camel uses for a value"
                                  + " of the route: set it with setVariable (name: orderId) and read it with"
                                  + " ${variable.orderId}");
        // the quick fix is still the did-you-mean
        assertThat(QuickFixes.fixFor(msg, "            message: \"New order ${property.orderId}\"").newText())
                .isEqualTo("${exchangeProperty.orderId}");
        assertThat(propertyFunction(generator("- setProperty:\n    orderId: ORD-1001\n")))
                .contains("which reads the exchange property that setProperty sets; or keep orderId in a variable");
        // set as a variable: that is what to read
        msg = propertyFunction(generator("- setVariable:\n    name: orderId\n    constant: ORD-1001\n"));
        assertThat(msg).endsWith("Unknown function: property.orderId (did you mean ${variable.orderId}?): the route keeps"
                                 + " orderId in a variable (setVariable)");
        assertThat(QuickFixes.fixFor(msg, "            message: \"New order ${property.orderId}\"").newText())
                .isEqualTo("${variable.orderId}");
        // set as a header
        assertThat(propertyFunction(generator("- setHeader:\n    name: orderId\n    constant: ORD-1001\n")))
                .endsWith("(did you mean ${header.orderId}?): the route keeps orderId in a header (setHeader)");
        // not set: the exchange property, or a variable
        assertThat(propertyFunction(generator("- setBody:\n    constant: order\n")))
                .endsWith("(did you mean ${exchangeProperty.orderId}?); or keep orderId in a variable, which is what"
                          + " Camel uses for a value of the route: set it with setVariable (name: orderId) and read it"
                          + " with ${variable.orderId}");
        // an exchange property of Camel itself is what the hint says
        assertThat(SourceValidator.validate("order-generator.camel.yaml",
                generator(setProperty).replace("property.orderId", "property.CamelSplitIndex"), new DefaultCamelCatalog(),
                null)).anySatisfy(
                        m -> assertThat(m).endsWith(
                                "Unknown function: property.CamelSplitIndex (did you mean ${exchangeProperty.CamelSplitIndex}?)"));
    }
}
