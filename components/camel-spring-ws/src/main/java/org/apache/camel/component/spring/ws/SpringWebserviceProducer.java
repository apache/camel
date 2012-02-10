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
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ExchangeHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.core.SourceExtractor;
import org.springframework.ws.client.core.WebServiceMessageCallback;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.addressing.client.ActionCallback;
import org.springframework.ws.soap.client.core.SoapActionCallback;
import org.springframework.ws.transport.WebServiceMessageSender;
import org.springframework.ws.transport.http.CommonsHttpMessageSender;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;
import org.springframework.ws.transport.http.HttpsUrlConnectionMessageSender;

public class SpringWebserviceProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SpringWebserviceProducer.class);
    private static final SourceExtractor<Object> SOURCE_EXTRACTOR = new NoopSourceExtractor();

    public SpringWebserviceProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public SpringWebserviceEndpoint getEndpoint() {
        return (SpringWebserviceEndpoint) super.getEndpoint();
    }

    public void process(Exchange exchange) throws Exception {
        // Let Camel TypeConverter hierarchy handle the conversion of XML messages to Source objects
        Source sourcePayload = exchange.getIn().getMandatoryBody(Source.class);

        // Extract optional headers
        String endpointUri = exchange.getIn().getHeader(SpringWebserviceConstants.SPRING_WS_ENDPOINT_URI, String.class);
        String soapAction = exchange.getIn().getHeader(SpringWebserviceConstants.SPRING_WS_SOAP_ACTION, String.class);
        URI wsAddressingAction = exchange.getIn().getHeader(SpringWebserviceConstants.SPRING_WS_ADDRESSING_ACTION, URI.class);

        // Populate the given (read) timeout if any
        populateTimeout(getEndpoint().getConfiguration());

        WebServiceMessageCallback callback = new DefaultWebserviceMessageCallback(soapAction, wsAddressingAction, getEndpoint().getConfiguration());
        Object body = null;
        if (endpointUri != null) {
            body = getEndpoint().getConfiguration().getWebServiceTemplate().sendSourceAndReceive(endpointUri, sourcePayload, callback, SOURCE_EXTRACTOR);
        } else {
            body = getEndpoint().getConfiguration().getWebServiceTemplate().sendSourceAndReceive(sourcePayload, callback, SOURCE_EXTRACTOR);
        }
        if (ExchangeHelper.isOutCapable(exchange)) {
            exchange.getOut().copyFrom(exchange.getIn());
            exchange.getOut().setBody(body);
        }
    }

    private static void populateTimeout(SpringWebserviceConfiguration configuration) {
        WebServiceTemplate webServiceTemplate = configuration.getWebServiceTemplate();
        List<WebServiceMessageSender> webServiceMessageSenders = new ArrayList<WebServiceMessageSender>(webServiceTemplate.getMessageSenders().length);
        Collections.addAll(webServiceMessageSenders, webServiceTemplate.getMessageSenders());
        for (WebServiceMessageSender webServiceMessageSender : webServiceMessageSenders) {
            if (webServiceMessageSender instanceof CommonsHttpMessageSender) {
                CommonsHttpMessageSender commonsHttpMessageSender = (CommonsHttpMessageSender) webServiceMessageSender;
                setTimeOut(commonsHttpMessageSender, configuration);
            } else if (webServiceMessageSender instanceof HttpsUrlConnectionMessageSender) {
                // Should check HttpsUrlConnectionMessageSender first as it extends HttpUrlConnectionMessageSender
                if (shouldConsiderTimeoutConfiguration(configuration)) {
                    webServiceMessageSenders.remove(webServiceMessageSender);
                    webServiceMessageSenders.add(new CamelHttpsUrlConnectionMessageSender(configuration));
                }
            } else if (webServiceMessageSender instanceof HttpUrlConnectionMessageSender) {
                if (shouldConsiderTimeoutConfiguration(configuration)) {
                    webServiceMessageSenders.remove(webServiceMessageSender);
                    webServiceMessageSenders.add(new CamelHttpUrlConnectionMessageSender(configuration));
                }
            } else {
                // Warn only if the timeout option has been explicitly specified
                if (shouldConsiderTimeoutConfiguration(configuration)) {
                    LOG.warn("Ignoring the timeout option for {} as there's no provided API available to populate it!", webServiceMessageSender);
                }
            }
        }

        webServiceTemplate.setMessageSenders(webServiceMessageSenders.toArray(new WebServiceMessageSender[webServiceMessageSenders.size()]));
    }

    private static boolean shouldConsiderTimeoutConfiguration(SpringWebserviceConfiguration configuration) {
        return configuration.getTimeout() > -1;
    }

    private static void setTimeOut(HttpURLConnection connection, SpringWebserviceConfiguration configuration) {
        if (configuration.getTimeout() > -1) {
            connection.setReadTimeout(configuration.getTimeout());
        }
    }

    private static void setTimeOut(CommonsHttpMessageSender commonsHttpMessageSender, SpringWebserviceConfiguration configuration) {
        if (configuration.getTimeout() > -1) {
            commonsHttpMessageSender.setReadTimeout(configuration.getTimeout());
        }
    }

    private static class CamelHttpUrlConnectionMessageSender extends HttpUrlConnectionMessageSender {

        private final SpringWebserviceConfiguration configuration;

        CamelHttpUrlConnectionMessageSender(SpringWebserviceConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        protected void prepareConnection(HttpURLConnection connection) throws IOException {
            super.prepareConnection(connection);

            setTimeOut(connection, configuration);
        }

    }

    private static class CamelHttpsUrlConnectionMessageSender extends HttpsUrlConnectionMessageSender {

        private final SpringWebserviceConfiguration configuration;

        CamelHttpsUrlConnectionMessageSender(SpringWebserviceConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        protected void prepareConnection(HttpURLConnection connection) throws IOException {
            super.prepareConnection(connection);

            setTimeOut(connection, configuration);
        }

    }

    private static class DefaultWebserviceMessageCallback implements WebServiceMessageCallback {
        private final String soapActionHeader;
        private final URI wsAddressingActionHeader;
        private final SpringWebserviceConfiguration configuration;

        public DefaultWebserviceMessageCallback(String soapAction, URI wsAddressingAction, SpringWebserviceConfiguration configuration) {
            this.soapActionHeader = soapAction;
            this.wsAddressingActionHeader = wsAddressingAction;
            this.configuration = configuration;
        }

        public void doWithMessage(WebServiceMessage message) throws IOException, TransformerException {
            // Add SoapAction to webservice request. Note that exchange header
            // takes precedence over endpoint option
            String soapAction = soapActionHeader != null ? soapActionHeader : configuration.getSoapAction();
            if (soapAction != null) {
                new SoapActionCallback(soapAction).doWithMessage(message);
            }
            // Add WS-Addressing Action to webservice request (the WS-Addressing
            // 'to' header will default to the URL of the connection).
            // Note that exchange header takes precedence over endpoint option
            URI wsAddressingAction = wsAddressingActionHeader != null ? wsAddressingActionHeader : configuration.getWsAddressingAction();
            if (wsAddressingAction != null) {
                new ActionCallback(wsAddressingAction).doWithMessage(message);
            }
        }
    }

    /**
     * A {@link SourceExtractor} that performs no conversion, instead conversion
     * is handled by Camel's {@link TypeConverter} hierarchy.
     */
    private static class NoopSourceExtractor implements SourceExtractor<Object> {
        public Object extractData(Source source) throws IOException, TransformerException {
            return source;
        }
    }
}
