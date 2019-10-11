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
package org.apache.camel.component.undertow;

import java.io.IOException;
import java.util.Map;

import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.server.HttpServerExchange;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.HeaderFilterStrategy;

/**
 * Interface to define custom binding for the component
 */
public interface UndertowHttpBinding {

    Message toCamelMessage(HttpServerExchange httpExchange, Exchange exchange) throws Exception;

    Message toCamelMessage(ClientExchange clientExchange, Exchange exchange) throws Exception;

    void populateCamelHeaders(HttpServerExchange httpExchange, Map<String, Object> headerMap, Exchange exchange) throws Exception;

    void populateCamelHeaders(ClientResponse response, Map<String, Object> headerMap, Exchange exchange) throws Exception;

    Object toHttpResponse(HttpServerExchange httpExchange, Message message) throws IOException;

    Object toHttpRequest(ClientRequest clientRequest, Message message);

    void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy);
    
    void setTransferException(Boolean transferException);

    void setMuteException(Boolean muteException);

}
