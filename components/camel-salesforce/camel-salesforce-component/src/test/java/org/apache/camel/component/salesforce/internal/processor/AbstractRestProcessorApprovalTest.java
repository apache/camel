/**
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
package org.apache.camel.component.salesforce.internal.processor;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.salesforce.SalesforceComponent;
import org.apache.camel.component.salesforce.SalesforceEndpoint;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.approval.ApprovalRequest;
import org.apache.camel.component.salesforce.api.dto.approval.ApprovalRequest.Action;
import org.apache.camel.component.salesforce.api.dto.approval.ApprovalRequests;
import org.apache.camel.component.salesforce.internal.client.RestClient;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultMessage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class AbstractRestProcessorApprovalTest {

    static class TestRestProcessor extends AbstractRestProcessor {

        RestClient client;

        TestRestProcessor() throws SalesforceException {
            this(endpoint(), mock(RestClient.class));
        }

        TestRestProcessor(final SalesforceEndpoint endpoint, final RestClient client) {
            super(endpoint, client, Collections.emptyMap());
            this.client = client;
        }

        static SalesforceComponent component() {
            return new SalesforceComponent();
        }

        static SalesforceEndpointConfig configuration() {
            return new SalesforceEndpointConfig();
        }

        static SalesforceEndpoint endpoint() {
            return new SalesforceEndpoint(notUsed(), component(), configuration(), notUsed(), notUsed());
        }

        @Override
        protected InputStream getRequestStream(final Exchange exchange) throws SalesforceException {
            return null;
        }

        @Override
        protected InputStream getRequestStream(final Object object) throws SalesforceException {
            return null;
        }

        @Override
        protected void processRequest(final Exchange exchange) throws SalesforceException {
        }

        @Override
        protected void processResponse(final Exchange exchange, final InputStream responseEntity,
                final Map<String, String> headers, final SalesforceException ex, final AsyncCallback callback) {
        }
    }

    private static <T> T notUsed() {
        return null;
    }

    static ApprovalRequest requestWithComment(final String comment) {
        final ApprovalRequest approvalRequest = new ApprovalRequest();
        approvalRequest.setComments(comment);

        return approvalRequest;
    }

    @Test
    public void shouldApplyTemplateToRequestFromBody() throws SalesforceException {
        final ApprovalRequest template = new ApprovalRequest();
        template.setActionType(Action.Submit);

        final ApprovalRequest approvalRequest = new ApprovalRequest();
        approvalRequest.setComments("it should be me");

        final TestRestProcessor processor = sendBodyAndHeader(approvalRequest, template);

        verify(processor).getRequestStream(new ApprovalRequests(approvalRequest.applyTemplate(template)));
    }

    @Test
    public void shouldApplyTemplateToRequestsFromBody() throws SalesforceException {
        final ApprovalRequest template = new ApprovalRequest();
        template.setActionType(Action.Submit);

        final ApprovalRequest approvalRequest1 = new ApprovalRequest();
        approvalRequest1.setComments("it should be me first");

        final ApprovalRequest approvalRequest2 = new ApprovalRequest();
        approvalRequest2.setComments("it should be me second");

        final TestRestProcessor processor = sendBodyAndHeader(Arrays.asList(approvalRequest1, approvalRequest2),
                template);

        verify(processor).getRequestStream(new ApprovalRequests(
                Arrays.asList(approvalRequest1.applyTemplate(template), approvalRequest2.applyTemplate(template))));
    }

    @Test
    public void shouldComplainIfNoHeaderGivenOrBodyIsEmptyIterable() {
        try {
            sendBodyAndHeader(Collections.EMPTY_LIST, null);
            fail("SalesforceException should be thrown");
        } catch (final SalesforceException e) {
            assertEquals("Exception should be about not giving a body or a header",
                    "Missing approval parameter in header or ApprovalRequest or List of ApprovalRequests body",
                    e.getMessage());
        }
    }

    @Test
    public void shouldComplainIfNoHeaderOrBodyIsGiven() {
        try {
            sendBodyAndHeader(null, null);
            fail("SalesforceException should be thrown");
        } catch (final SalesforceException e) {
            assertEquals("Exception should be about not giving a body or a header",
                    "Missing approval parameter in header or ApprovalRequest or List of ApprovalRequests body",
                    e.getMessage());
        }
    }

    @Test
    public void shouldFetchApprovalRequestFromBody() throws SalesforceException {
        final ApprovalRequest approvalRequest = new ApprovalRequest();
        approvalRequest.setComments("it should be me");

        final TestRestProcessor processor = sendBody(approvalRequest);

        verify(processor).getRequestStream(new ApprovalRequests(approvalRequest));
    }

    @Test
    public void shouldFetchApprovalRequestFromHeader() throws SalesforceException {
        final ApprovalRequest request = new ApprovalRequest();
        request.setComments("hi there");
        final TestRestProcessor processor = sendBodyAndHeader(null, request);

        verify(processor).getRequestStream(new ApprovalRequests(request));
    }

    @Test
    public void shouldFetchApprovalRequestFromHeaderEvenIfBodyIsDefinedButNotConvertable() throws SalesforceException {
        final ApprovalRequest request = new ApprovalRequest();
        request.setComments("hi there");

        final TestRestProcessor processor = sendBodyAndHeaders("Nothing to see here", request,
                Collections.singletonMap("approval.ContextId", "context-id"));

        final ApprovalRequest combined = new ApprovalRequest();
        combined.setComments("hi there");
        combined.setContextId("context-id");

        verify(processor).getRequestStream(new ApprovalRequests(combined));
    }

    @Test
    public void shouldFetchApprovalRequestsFromBody() throws SalesforceException {
        final ApprovalRequest approvalRequest1 = new ApprovalRequest();
        approvalRequest1.setComments("it should be me first");

        final ApprovalRequest approvalRequest2 = new ApprovalRequest();
        approvalRequest2.setComments("it should be me second");

        final TestRestProcessor processor = sendBody(Arrays.asList(approvalRequest1, approvalRequest2));

        verify(processor).getRequestStream(new ApprovalRequests(Arrays.asList(approvalRequest1, approvalRequest2)));
    }

    @Test
    public void shouldFetchApprovalRequestsFromMultiplePropertiesInMessageHeaders() throws SalesforceException {
        final Map<String, Object> headers = new HashMap<>();
        headers.put("approval.ContextId", "contextId");

        final TestRestProcessor processor = sendBodyAndHeaders(notUsed(), notUsed(), headers);

        final ApprovalRequest request = new ApprovalRequest();
        request.setContextId("contextId");

        verify(processor).getRequestStream(new ApprovalRequests(request));
    }

    @Test
    public void shouldHonorPriorities() throws SalesforceException {
        final ApprovalRequest template = new ApprovalRequest();
        template.setComments("third priority");

        final ApprovalRequest body = new ApprovalRequest();
        body.setComments("first priority");

        final Map<String, Object> headers = Collections.singletonMap("approval.Comments", "second priority");

        final TestRestProcessor processor1 = sendBodyAndHeaders(null, template, null);

        verify(processor1).getRequestStream(new ApprovalRequests(requestWithComment("third priority")));

        final TestRestProcessor processor2 = sendBodyAndHeaders(null, template, headers);
        verify(processor2).getRequestStream(new ApprovalRequests(requestWithComment("second priority")));

        final TestRestProcessor processor3 = sendBodyAndHeaders(body, template, headers);
        verify(processor3).getRequestStream(new ApprovalRequests(requestWithComment("first priority")));
    }

    TestRestProcessor sendBody(final Object body) throws SalesforceException {
        return sendBodyAndHeader(body, null);
    }

    TestRestProcessor sendBodyAndHeader(final Object body, final ApprovalRequest template) throws SalesforceException {
        return sendBodyAndHeaders(body, template, Collections.emptyMap());
    }

    TestRestProcessor sendBodyAndHeaders(final Object body, final ApprovalRequest template,
            final Map<String, Object> headers) throws SalesforceException {
        final TestRestProcessor processor = spy(new TestRestProcessor());

        final CamelContext context = new DefaultCamelContext();
        final Exchange exchange = new DefaultExchange(context);

        final Message message = new DefaultMessage(context);
        if (headers != null) {
            message.setHeaders(headers);
        }
        message.setHeader(SalesforceEndpointConfig.APPROVAL, template);

        message.setBody(body);

        exchange.setIn(message);

        processor.processApproval(exchange, notUsed());

        return processor;
    }
}
