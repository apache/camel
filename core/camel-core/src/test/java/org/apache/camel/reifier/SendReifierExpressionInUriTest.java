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
package org.apache.camel.reifier;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.log.ConsumingAppender;
import org.apache.camel.component.mock.MockComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A to resolves its endpoint once, when the route is created, so a Simple expression in the path of its uri is sent as
 * text. The route still starts, and a WARN names toD, except where the component evaluates its path for each message.
 */
public class SendReifierExpressionInUriTest extends ContextTestSupport {

    private static final String LOGGER = SendReifier.class.getName();
    private static final String APPENDER = "SendReifierExpressionInUriTest";

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private Appender appender;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        // the component json of these test components is in META-INF/org/apache/camel/reifier
        camelContext.addComponent("evaluatedpath", new TestComponent());
        camelContext.addComponent("fixedpath", new TestComponent());
        camelContext.addComponent("nometadata", new TestComponent());
        camelContext.getPropertiesComponent().addInitialProperty("target.uri", "mock:placeholder-${header.id}");
        return camelContext;
    }

    @BeforeEach
    public void captureWarnings() {
        // test classes run in parallel in one JVM: keep only the WARNs about the routes of this test
        appender = ConsumingAppender.newAppender(LOGGER, APPENDER, Level.WARN, event -> {
            String message = event.getMessage().getFormattedMessage();
            if (event.getLevel().isMoreSpecificThan(Level.WARN) && message.contains("expression-in-uri-")) {
                warnings.add(message);
            }
        });
        // the routes the tests add are created at once in a started context
        context.start();
    }

    @AfterEach
    public void stopCapture() {
        LoggerContext logging = (LoggerContext) LogManager.getContext(false);
        logging.getConfiguration().removeLogger(LOGGER);
        logging.updateLoggers();
        appender.stop();
    }

    @Test
    public void toWithExpressionInPathWarnsAndSendsToTheLiteralEndpoint() throws Exception {
        addRoute("expression-in-uri-mock", "direct:mock", "mock:out-${header.id}");

        assertThat(warnings).singleElement().asString()
                .contains("expression-in-uri-mock", "mock:out-${header.id}", "${header.id}", "toD");

        template.sendBodyAndHeader("direct:mock", "hello", "id", "1");

        // the behaviour is unchanged: one endpoint, named by the text of the expression, gets every message
        assertThat(context.hasEndpoint("mock://out-1")).isNull();
        Endpoint literal = context.hasEndpoint("mock://out-$%7Bheader.id%7D");
        assertThat(literal).isInstanceOf(MockEndpoint.class);
        assertThat(((MockEndpoint) literal).getReceivedCounter()).isEqualTo(1);
    }

    @Test
    public void toWithExpressionFromPropertyPlaceholderWarns() throws Exception {
        addRoute("expression-in-uri-placeholder", "direct:placeholder", "{{target.uri}}");

        assertThat(warnings).singleElement().asString()
                .contains("expression-in-uri-placeholder", "${header.id}", "toD");
    }

    @Test
    public void toWithExpressionInPathOfComponentWithoutPathMetadataWarns() throws Exception {
        addRoute("expression-in-uri-fixed", "direct:fixed", "fixedpath:orders-${header.region}");

        assertThat(warnings).singleElement().asString()
                .contains("expression-in-uri-fixed", "${header.region}", "toD");
    }

    @Test
    public void toDDoesNotWarnAndSendsToThePerMessageEndpoint() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:dynamic").routeId("expression-in-uri-tod").toD("mock:out-${header.id}");
            }
        });
        template.sendBodyAndHeader("direct:dynamic", "hello", "id", "2");

        assertThat(warnings).isEmpty();
        Endpoint out = context.hasEndpoint("mock://out-2");
        assertThat(out).isInstanceOf(MockEndpoint.class);
        assertThat(((MockEndpoint) out).getReceivedCounter()).isEqualTo(1);
    }

    @Test
    public void expressionOnlyInTheOptionsDoesNotWarn() throws Exception {
        addRoute("expression-in-uri-option", "direct:option", "log:orders?marker=${header.marker}");

        assertThat(warnings).isEmpty();
    }

    @Test
    public void languageComponentDoesNotWarn() throws Exception {
        // the path of the language component is a script, not an address
        addRoute("expression-in-uri-language", "direct:language", "language:simple:Hello ${body}");

        assertThat(warnings).isEmpty();
        assertThat(template.requestBody("direct:language", "World", String.class)).isEqualTo("Hello World");
    }

    @Test
    public void componentThatEvaluatesItsPathDoesNotWarn() throws Exception {
        // its component json says the path supports a Simple expression (supportSimpleExpression)
        addRoute("expression-in-uri-evaluated", "direct:evaluated", "evaluatedpath:orders.${header.region}");

        assertThat(warnings).isEmpty();
    }

    @Test
    public void componentWithoutMetadataDoesNotWarn() throws Exception {
        // nothing says what the component does with its path: say nothing rather than the wrong thing
        addRoute("expression-in-uri-unknown", "direct:unknown", "nometadata:orders-${header.region}");

        assertThat(warnings).isEmpty();
    }

    @Test
    public void parameterBoundPerMessageDoesNotWarn() throws Exception {
        // :#${...} and :#in:${...} are parameters the sql components bind for each message
        addRoute("expression-in-uri-bound", "direct:bound", "fixedpath:select * from orders where id = :#${body}");
        addRoute("expression-in-uri-bound-in", "direct:boundIn",
                "fixedpath:select * from orders where id in (:#in:${body})");

        assertThat(warnings).isEmpty();
    }

    private void addRoute(String routeId, String from, String to) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(from).routeId(routeId).to(to);
            }
        });
    }

    /**
     * A mock component of its own package, where the test component json is.
     */
    private static final class TestComponent extends MockComponent {
    }
}
