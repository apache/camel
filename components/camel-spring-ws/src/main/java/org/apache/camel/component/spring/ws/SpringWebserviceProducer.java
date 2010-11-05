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
package org.apache.camel.component.spring.ws;

import java.io.IOException;
import java.net.URI;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultProducer;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.core.SourceExtractor;
import org.springframework.ws.client.core.WebServiceMessageCallback;
import org.springframework.ws.soap.addressing.client.ActionCallback;
import org.springframework.ws.soap.client.core.SoapActionCallback;

public class SpringWebserviceProducer extends DefaultProducer {

    private static final SourceExtractor SOURCE_EXTRACTOR = new NoopSourceExtractor();
    private SpringWebserviceEndpoint endpoint;

    public SpringWebserviceProducer(Endpoint endpoint) {
        super(endpoint);
        this.endpoint = (SpringWebserviceEndpoint) endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        // Let Camel TypeConverter hierarchy handle the conversion of XML messages to Source objects
        Source sourcePayload = exchange.getIn().getMandatoryBody(Source.class);

        // Extract optional headers
        String endpointUri = exchange.getIn().getHeader(SpringWebserviceConstants.SPRING_WS_ENDPOINT_URI, String.class);
        String soapAction = exchange.getIn().getHeader(SpringWebserviceConstants.SPRING_WS_SOAP_ACTION, String.class);
        URI wsAddressingAction = exchange.getIn().getHeader(SpringWebserviceConstants.SPRING_WS_ADDRESSING_ACTION, URI.class);

        WebServiceMessageCallback callback = new DefaultWebserviceMessageCallback(soapAction, wsAddressingAction);
        Object body = null;
        if (endpointUri != null) {
            body = endpoint.getConfiguration().getWebServiceTemplate().sendSourceAndReceive(endpointUri, sourcePayload, callback, SOURCE_EXTRACTOR);
        } else {
            body = endpoint.getConfiguration().getWebServiceTemplate().sendSourceAndReceive(sourcePayload, callback, SOURCE_EXTRACTOR);
        }
        exchange.getOut().setBody(body);
    }

    protected class DefaultWebserviceMessageCallback implements WebServiceMessageCallback {
        private String soapActionHeader;
        private URI wsAddressingActionHeader;

        public DefaultWebserviceMessageCallback(String soapAction, URI wsAddressingAction) {
            this.soapActionHeader = soapAction;
            this.wsAddressingActionHeader = wsAddressingAction;
        }

        public void doWithMessage(WebServiceMessage message) throws IOException, TransformerException {
            // Add SoapAction to webservice request. Note that exchange header
            // takes precedence over endpoint option
            String soapAction = soapActionHeader != null ? soapActionHeader : endpoint.getConfiguration().getSoapAction();
            if (soapAction != null) {
                new SoapActionCallback(soapAction).doWithMessage(message);
            }
            // Add WS-Addressing Action to webservice request (the WS-Addressing
            // 'to' header will default to the URL of the connection).
            // Note that exchange header takes precedence over endpoint option
            URI wsAddressingAction = wsAddressingActionHeader != null ? wsAddressingActionHeader : endpoint.getConfiguration().getWsAddressingAction();
            if (wsAddressingAction != null) {
                new ActionCallback(wsAddressingAction).doWithMessage(message);
            }
        }
    }

    /**
     * A {@link SourceExtractor} that performs no conversion, instead conversion
     * is handled by Camel's {@link TypeConverter} hierarchy.
     */
    private static class NoopSourceExtractor implements SourceExtractor {
        public Object extractData(Source source) throws IOException, TransformerException {
            return source;
        }
    }
}
