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
package org.apache.camel.component.spring.ws.bean;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.CamelContext;
import org.springframework.ws.transport.WebServiceConnection;
import org.springframework.ws.transport.WebServiceMessageSender;

/**
 * Use this class with conjuctions of wsa:replyTo custom routing using prefix
 * direct Received message will be route like this: <to uri="direct:uri" />
 */
public class CamelDirectSender implements WebServiceMessageSender {

    private CamelContext camelContext;

    @Override
    public WebServiceConnection createConnection(URI uri) throws IOException {
        try {
            return new CamelDirectConnection(camelContext, uri);
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean supports(URI uri) {
        try {
            new CamelDirectConnection(camelContext, uri);
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

}
