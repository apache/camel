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
package org.apache.camel.component.http;

import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.HandlerCollection;

/**
 * Ported from {@link org.apache.camel.component.http4.BaseHttpTest}.
 *
 */
public abstract class BaseHttpTest extends CamelTestSupport {

    protected void assertExchange(Exchange exchange) {
        assertNotNull(exchange);

        assertTrue(exchange.hasOut());
        Message out = exchange.getOut();
        assertHeaders(out.getHeaders());
        assertBody(out.getBody(String.class));
    }

    protected void assertHeaders(Map<String, Object> headers) {
        assertEquals(HttpServletResponse.SC_OK, headers.get(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("12", headers.get("Content-Length"));
        assertNotNull("Should have Content-Type header", headers.get("Content-Type"));
    }

    protected void assertBody(String body) {
        assertEquals(getExpectedContent(), body);
    }

    protected String getExpectedContent() {
        return "camel rocks!";
    }

    protected ContextHandler contextHandler(String context, Handler handler) {
        ContextHandler contextHandler = new ContextHandler(context);
        contextHandler.setHandler(handler);
        return contextHandler;
    }

    protected HandlerCollection handlers(Handler... handlers) {
        HandlerCollection collection = new ContextHandlerCollection();
        collection.setHandlers(handlers);
        return collection;
    }

}
