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
package org.apache.camel.component.google.mail.transform;

import java.util.List;

import com.google.api.services.gmail.model.ModifyMessageRequest;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.mail.GoogleMailComponent;
import org.apache.camel.component.google.mail.GoogleMailConfiguration;
import org.apache.camel.component.google.mail.MockGoogleMailClientFactory;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GoogleMailUpdateMessageLabelsDataTypeTransformerTest extends CamelTestSupport {

    private static final String LABELS_RESPONSE = """
            {
              "labels": [
                {"id": "INBOX", "name": "INBOX"},
                {"id": "UNREAD", "name": "UNREAD"},
                {"id": "Label_789", "name": "Work"},
                {"id": "Label_456", "name": "Another Label"}
              ]
            }
            """;

    private GoogleMailUpdateMessageLabelsDataTypeTransformer transformer;
    private MockGoogleMailClientFactory mockClientFactory;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = new DefaultCamelContext();

        GoogleMailConfiguration configuration = new GoogleMailConfiguration();
        configuration.setClientId("mock-client-id");
        configuration.setClientSecret("mock-client-secret");
        configuration.setApplicationName("mock");

        mockClientFactory = new MockGoogleMailClientFactory(LABELS_RESPONSE);

        final GoogleMailComponent component = new GoogleMailComponent(context);
        component.setConfiguration(configuration);
        component.setClientFactory(mockClientFactory);

        context.addComponent("google-mail", component);

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:input")
                        .transformDataType("google-mail:update-message-labels")
                        .to("mock:result");
            }
        };
    }

    @Override
    protected void doPostSetup() {
        transformer = new GoogleMailUpdateMessageLabelsDataTypeTransformer();
        transformer.setCamelContext(context);
    }

    @Test
    void testAddAndRemoveLabels() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.setVariable("addLabels", List.of("Work"));
        exchange.setVariable("removeLabels", List.of("INBOX"));

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        ModifyMessageRequest request = exchange.getMessage().getBody(ModifyMessageRequest.class);
        assertNotNull(request);
        assertEquals(List.of("Label_789"), request.getAddLabelIds());
        assertEquals(List.of("INBOX"), request.getRemoveLabelIds());
    }

    @Test
    void testCommaSeparatedString() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.setVariable("addLabels", "Work,Another Label");
        exchange.setVariable("removeLabels", "INBOX");

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        ModifyMessageRequest request = exchange.getMessage().getBody(ModifyMessageRequest.class);
        assertNotNull(request);
        assertEquals(List.of("Label_789", "Label_456"), request.getAddLabelIds());
        assertEquals(List.of("INBOX"), request.getRemoveLabelIds());
    }

    @Test
    void testOnlyAddLabels() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.setVariable("addLabels", List.of("Work"));

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        ModifyMessageRequest request = exchange.getMessage().getBody(ModifyMessageRequest.class);
        assertNotNull(request);
        assertEquals(List.of("Label_789"), request.getAddLabelIds());
        assertNull(request.getRemoveLabelIds());
    }

    @Test
    void testOnlyRemoveLabels() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.setVariable("removeLabels", List.of("INBOX"));

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        ModifyMessageRequest request = exchange.getMessage().getBody(ModifyMessageRequest.class);
        assertNotNull(request);
        assertNull(request.getAddLabelIds());
        assertEquals(List.of("INBOX"), request.getRemoveLabelIds());
    }

    @Test
    void testNoLabelsThrowsException() {
        Exchange exchange = new DefaultExchange(context);

        assertThrows(CamelExecutionException.class,
                () -> transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY));
    }

    @Test
    void testUnknownLabelThrowsException() {
        Exchange exchange = new DefaultExchange(context);
        exchange.setVariable("addLabels", List.of("NonExistentLabel"));

        assertThrows(CamelExecutionException.class,
                () -> transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY));
    }

    @Test
    void testExplicitUserId() throws Exception {
        int urlCountBefore = mockClientFactory.getRequestUrls().size();

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader("CamelGoogleMail.userId", "user@example.com");
        exchange.setVariable("addLabels", List.of("Work"));

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        ModifyMessageRequest request = exchange.getMessage().getBody(ModifyMessageRequest.class);
        assertNotNull(request);
        assertEquals(List.of("Label_789"), request.getAddLabelIds());

        // Verify the userId was forwarded to the Gmail API
        List<String> newUrls = mockClientFactory.getRequestUrls().subList(urlCountBefore,
                mockClientFactory.getRequestUrls().size());
        assertTrue(newUrls.stream().anyMatch(url -> url.contains("/users/user@example.com/")),
                "Expected Gmail API call with userId 'user@example.com', but URLs were: " + newUrls);
    }

    @Test
    void testTransformerName() {
        assertEquals("google-mail:update-message-labels", transformer.getName());
    }

    // E2E tests via Camel route)

    @Test
    void testAddAndRemoveLabelsViaRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.reset();
        mock.expectedMessageCount(1);

        template.send("direct:input", exchange -> {
            exchange.setVariable("addLabels", List.of("Work"));
            exchange.setVariable("removeLabels", List.of("INBOX"));
        });

        mock.assertIsSatisfied();

        Exchange received = mock.getExchanges().get(0);
        ModifyMessageRequest request = received.getMessage().getBody(ModifyMessageRequest.class);
        assertNotNull(request);
        assertEquals(List.of("Label_789"), request.getAddLabelIds());
        assertEquals(List.of("INBOX"), request.getRemoveLabelIds());
    }

    @Test
    void testCommaSeparatedLabelsViaRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.reset();
        mock.expectedMessageCount(1);

        template.send("direct:input", exchange -> {
            exchange.setVariable("addLabels", "Work,Another Label");
            exchange.setVariable("removeLabels", "INBOX");
        });

        mock.assertIsSatisfied();

        Exchange received = mock.getExchanges().get(0);
        ModifyMessageRequest request = received.getMessage().getBody(ModifyMessageRequest.class);
        assertNotNull(request);
        assertEquals(List.of("Label_789", "Label_456"), request.getAddLabelIds());
        assertEquals(List.of("INBOX"), request.getRemoveLabelIds());
    }

    @Test
    void testOnlyAddLabelsViaRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.reset();
        mock.expectedMessageCount(1);

        template.send("direct:input", exchange -> {
            exchange.setVariable("addLabels", List.of("Work"));
        });

        mock.assertIsSatisfied();

        Exchange received = mock.getExchanges().get(0);
        ModifyMessageRequest request = received.getMessage().getBody(ModifyMessageRequest.class);
        assertNotNull(request);
        assertEquals(List.of("Label_789"), request.getAddLabelIds());
    }

    @Test
    void testOnlyRemoveLabelsViaRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.reset();
        mock.expectedMessageCount(1);

        template.send("direct:input", exchange -> {
            exchange.setVariable("removeLabels", List.of("INBOX"));
        });

        mock.assertIsSatisfied();

        Exchange received = mock.getExchanges().get(0);
        ModifyMessageRequest request = received.getMessage().getBody(ModifyMessageRequest.class);
        assertNotNull(request);
        assertNull(request.getAddLabelIds());
        assertEquals(List.of("INBOX"), request.getRemoveLabelIds());
    }
}
