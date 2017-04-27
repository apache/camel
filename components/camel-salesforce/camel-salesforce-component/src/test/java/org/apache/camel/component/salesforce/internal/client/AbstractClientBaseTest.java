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
package org.apache.camel.component.salesforce.internal.client;

import java.io.InputStream;

import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.eclipse.jetty.client.HttpConversation;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Response.CompleteListener;
import org.eclipse.jetty.client.api.Result;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbstractClientBaseTest {
    static class Client extends AbstractClientBase {
        Client(final SalesforceSession session) throws SalesforceException {
            super(null, session, mock(SalesforceHttpClient.class),
                1 /* 1 second termination timeout */);
        }

        @Override
        protected SalesforceException createRestException(final Response response, final InputStream responseContent) {
            return null;
        }

        @Override
        protected void setAccessToken(final Request request) {
        }

    }

    SalesforceSession session = mock(SalesforceSession.class);

    // having client as a field also tests that the same client instance can be
    // stopped and started again
    final Client client;

    public AbstractClientBaseTest() throws SalesforceException {
        client = new Client(session);

        when(session.getAccessToken()).thenReturn("token");
    }

    @Before
    public void startClient() throws Exception {
        client.start();
    }

    @Test
    public void shouldNotHangIfRequestsHaveFinished() throws Exception {
        final Request request = mock(Request.class);
        final ArgumentCaptor<CompleteListener> listener = ArgumentCaptor.forClass(CompleteListener.class);

        doNothing().when(request).send(listener.capture());

        client.doHttpRequest(request, (response, exception) -> {
        });

        final Result result = mock(Result.class);
        final Response response = mock(Response.class);
        when(result.getResponse()).thenReturn(response);

        final SalesforceHttpRequest salesforceRequest = mock(SalesforceHttpRequest.class);
        when(result.getRequest()).thenReturn(salesforceRequest);

        final HttpConversation conversation = mock(HttpConversation.class);
        when(salesforceRequest.getConversation()).thenReturn(conversation);

        when(conversation.getAttribute(SalesforceSecurityHandler.AUTHENTICATION_REQUEST_ATTRIBUTE))
            .thenReturn(salesforceRequest);

        // completes the request
        listener.getValue().onComplete(result);

        final long stopStartTime = System.currentTimeMillis();
        // should not wait
        client.stop();

        final long elapsed = System.currentTimeMillis() - stopStartTime;
        assertTrue(elapsed < 10);
    }

    @Test
    public void shouldTimeoutWhenRequestsAreStillOngoing() throws Exception {
        client.doHttpRequest(mock(Request.class), (response, exception) -> {
        });

        // the request never completes

        final long stopStartTime = System.currentTimeMillis();
        // will wait for 1 second
        client.stop();

        final long elapsed = System.currentTimeMillis() - stopStartTime;
        assertTrue(elapsed > 900 && elapsed < 1100);
    }
}
