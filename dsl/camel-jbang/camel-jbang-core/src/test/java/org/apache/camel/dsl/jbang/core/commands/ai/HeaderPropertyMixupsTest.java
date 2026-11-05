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
import java.util.List;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25516: a header, exchange property or variable read where the route keeps the name in another of them; and a
 * header copied to itself that nothing sets.
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

    @Test
    void aPropertyThatAScriptSetsIsNotPointedToTheVariable() {
        // r26 connect-sftp-transfer step 2: the route set the variable orderId to 0 first, then a Groovy script set the
        // exchange property; "did you mean ${variable.orderId}?" made the log read the 0
        String script = """
                - setVariable:
                    name: orderId
                    constant: "0"
                - setBody:
                    groovy: |
                      exchange.setProperty('orderId', 'ORD-' + exchange.getProperty('CamelTimerCounter'))
                      body
                """;
        String msg = propertyFunction(generator(script));
        assertThat(msg).endsWith("Unknown function: property.orderId (did you mean ${exchangeProperty.orderId}?), which"
                                 + " reads the exchange property that the script sets (exchange.setProperty); or keep"
                                 + " orderId in a variable instead, which is what Camel uses for a value of the route:"
                                 + " set it in the script with exchange.setVariable('orderId', ...) and read it with"
                                 + " ${variable.orderId}");
        assertThat(QuickFixes.fixFor(msg, "            message: \"New order ${property.orderId}\"").newText())
                .isEqualTo("${exchangeProperty.orderId}");
        // the other ways a script sets it, and a script that only reads it
        assertThat(propertyFunction(generator(script.replace("exchange.setProperty('orderId', ",
                "exchange.properties['orderId'] = (")))).contains("that the script sets");
        assertThat(propertyFunction(generator(script.replace("exchange.setProperty('orderId', ",
                "exchange.properties.orderId = (")))).contains("that the script sets");
        assertThat(propertyFunction(generator(script.replace("exchange.setProperty('orderId', ",
                "exchange.properties['orderId'] == (")))).contains("(did you mean ${variable.orderId}?)");
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

    /**
     * r27l-4 connect-http-client step 2 (shortened): in the split over the lines of an order, sku and needed copied
     * from headers that nothing sets. The call went to http://localhost:8080/stock/ with no sku: 98 errors of 404.
     */
    private static final String CHECK_STOCK = """
            - route:
                id: check-stock
                from:
                  uri: file
                  parameters:
                    directoryName: orders
                    noop: true
                  steps:
                    - unmarshal:
                        json:
                          library: Jackson
                    - setVariable:
                        name: orderId
                        expression:
                          simple:
                            expression: "${body[orderId]}"
                    - split:
                        expression:
                          simple:
                            expression: "${body[lines]}"
                        steps:
                          - setHeader:
                              name: sku
                              expression:
                                simple:
                                  expression: "${header.sku}"
                          - setHeader:
                              name: needed
                              expression:
                                simple:
                                  expression: "${header.qty}"
                          - setBody:
                              expression:
                                simple:
                                  expression: "${null}"
                          - toD:
                              uri: "http://localhost:8080/stock/${header.sku}"
                          - unmarshal:
                              json:
                                library: Jackson
                          - log:
                              message: "${variable.orderId}: ${header.sku} x ${header.needed}, ${body[qty]} in stock"
            """;

    /** The rest of connect-http-client, whose path parameter is a header of the stock service route. */
    private static final String REST = """
            - rest:
                path: /stock
                get:
                  - path: "/{sku}"
                    to:
                      uri: direct:one-sku
            """;

    @Test
    void aHeaderCopiedToItselfThatNothingSetsIsReported() {
        // the stock service of the same file has the path parameter {sku}: a header of the route the rest calls only
        assertThat(HeaderPropertyMixups.validate(CHECK_STOCK + REST)).singleElement().asString()
                .startsWith("Line 26: ${header.sku} is null here");
        assertThat(HeaderPropertyMixups.validate(CHECK_STOCK)).singleElement().isEqualTo(
                "Line 26: ${header.sku} is null here: no step before this line sets the header sku, so setHeader sku"
                                                                                         + " copies null to itself. In the split the body is the element: a field of it"
                                                                                         + " is ${body[sku]}; the same for ${header.qty} on line 31: ${body[qty]}");
    }

    @Test
    void theFieldsOfTheDataFilesSayWhatToRead(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("orders"));
        Files.writeString(dir.resolve("orders/order-1001.json"), """
                {"orderId": "ORD-1001", "customer": "C-482", "lines": [{"sku": "CAMEL-TSHIRT", "qty": 2, "price": 19.9}]}
                """);
        List<String> msgs = SourceValidator.validate("http-client.camel.yaml", CHECK_STOCK, new DefaultCamelCatalog(),
                null, dir);
        assertThat(msgs).singleElement().isEqualTo(
                "Line 26: ${header.sku} is null here: no step before this line sets the header sku, so setHeader sku"
                                                   + " copies null to itself. In the split the body is one of the lines of the"
                                                   + " files in orders/ (price, qty, sku): write ${body[sku]}; the same for"
                                                   + " ${header.qty} on line 31: ${body[qty]}");
        // r23l-3: the order id copied to itself in the split, and no variable for it: a field of the order, not of a line
        String orderId = CHECK_STOCK
                .replace("        - setVariable:\n            name: orderId\n            expression:\n              simple:\n"
                         + "                expression: \"${body[orderId]}\"\n",
                        "")
                .replace("name: sku\n                  expression:\n                    simple:\n"
                         + "                      expression: \"${header.sku}\"",
                        "name: orderId\n                  expression:\n                    simple:\n"
                                                                                  + "                      expression: \"${header.orderId}\"")
                .replace("${variable.orderId}: ", "${header.orderId}: ");
        assertThat(SourceValidator.validate("http-client.camel.yaml", orderId, new DefaultCamelCatalog(), null, dir))
                .singleElement().asString()
                .startsWith("Line 21: ${header.orderId} is null here: no step before this line sets the header orderId")
                .contains("orderId is a field of the data that was split, not of one of the lines: read it before the"
                          + " split, into a variable (setVariable), and write ${variable.orderId} in the split");
    }

    @Test
    void aHeaderCopiedToItselfIsNotReportedWhereItCanBeSet() {
        String split = "        - split:\n";
        // set before: no copy to itself is left, and a copy from another header alone is not reported
        assertThat(HeaderPropertyMixups.validate(CHECK_STOCK.replace(split,
                "        - setHeader:\n            name: sku\n            simple: \"${body[lines][0][sku]}\"\n" + split)))
                .isEmpty();
        // a step that may set any header before it: a to:, an enrich, a process, a script
        for (String step : List.of("- to: direct:prepare", "- enrich:\n    expression:\n      constant: direct:prepare",
                "- process:\n    ref: prepare", "- setBody:\n    groovy: \"headers.sku = 'A'; body\"")) {
            assertThat(HeaderPropertyMixups.validate(CHECK_STOCK.replace(split, step.indent(8) + split))).as(step)
                    .isEmpty();
        }
        // a consumer that can carry the header, or a caller that can set it
        for (String from : List.of("direct", "seda", "platform-http", "jms", "kafka")) {
            assertThat(HeaderPropertyMixups.validate(CHECK_STOCK.replace("uri: file", "uri: " + from))).as(from)
                    .isEmpty();
        }

        // r27l-6, a passed step: the order id copied to itself, and the log reads the variable: the null is never read
        assertThat(HeaderPropertyMixups.validate(CHECK_STOCK.replace("name: sku", "name: orderId")
                .replace("expression: \"${header.sku}\"", "expression: \"${header.orderId}\""))).isEmpty();
        // a header of Camel itself, which the consumer sets
        assertThat(HeaderPropertyMixups.validate(CHECK_STOCK.replace("name: sku", "name: CamelFileName")
                .replace("expression: \"${header.sku}\"", "expression: \"${header.CamelFileName}\""))).isEmpty();
    }

    @Test
    void aHeaderCopiedToItselfThatTheRouteKeepsInAVariable() {
        String copy = "            steps:\n              - setHeader:\n                  name: sku\n";
        String variable
                = "              - setVariable:\n                  name: sku\n                  simple: \"${body[sku]}\"\n";
        assertThat(HeaderPropertyMixups.validate(CHECK_STOCK.replace(copy,
                "            steps:\n" + variable + "              - setHeader:\n                  name: sku\n")))
                .singleElement().asString()
                .startsWith("Line 29: ${header.sku} is null here: no step before this line sets the header sku")
                .contains("The route keeps sku in a variable: write ${variable.sku}");
        // a variable set after the copy is not what the copy reads
        assertThat(HeaderPropertyMixups
                .validate(CHECK_STOCK.replace("              - setHeader:\n                  name: needed\n",
                        variable + "              - setHeader:\n                  name: needed\n")))
                .singleElement().asString()
                .contains("In the split the body is the element: a field of it is ${body[sku]}");
    }
}
