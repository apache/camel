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
package org.apache.camel.component.jetty;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultMessage;

/**
 * @version $Revision$
 */
public class JettyHttpMessage extends DefaultMessage {

    private final JettyContentExchange httpExchange;
    private final boolean throwExceptionOnFailure;

    public JettyHttpMessage(Exchange exchange, JettyContentExchange httpExchange, boolean throwExceptionOnFailure) {
        setExchange(exchange);
        this.httpExchange = httpExchange;
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    @Override
    protected void populateInitialHeaders(Map<String, Object> map) {
        if (httpExchange.isHeadersComplete()) {
            map.putAll(httpExchange.getHeaders());
        } else {
            // wait for headers to be done
            try {
                httpExchange.waitForHeadersToComplete();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // ignore
            }
            map.putAll(httpExchange.getHeaders());
        }
    }

    @Override
    protected Object createBody() {
        // return a Future which by end user can use to obtain the response later
        return new JettyFutureGetBody(getExchange(), httpExchange, throwExceptionOnFailure);
    }

}

