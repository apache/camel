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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
import org.apache.camel.util.ReflectionHelper;
import org.apache.camel.util.ReflectionHelper.FieldCallback;

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

    private static void populateTimeout(SpringWebserviceConfiguration configuration) throws Exception {
        if (!(configuration.getTimeout() > -1)) {
            return;
        }

        WebServiceTemplate webServiceTemplate = configuration.getWebServiceTemplate();

        // Can't use java.util.Arrays.asList() as it doesn't support the optional remove() operation which we need here
        List<WebServiceMessageSender> webServiceMessageSenders = new ArrayList<WebServiceMessageSender>(webServiceTemplate.getMessageSenders().length);
        Collections.addAll(webServiceMessageSenders, webServiceTemplate.getMessageSenders());
        for (WebServiceMessageSender webServiceMessageSender : webServiceMessageSenders) {
            if (webServiceMessageSender instanceof CommonsHttpMessageSender) {
                setTimeOut((CommonsHttpMessageSender) webServiceMessageSender, configuration);
            } else if (webServiceMessageSender instanceof HttpsUrlConnectionMessageSender) {
                // Should check HttpsUrlConnectionMessageSender beforehand as it extends HttpUrlConnectionMessageSender
                webServiceMessageSenders.remove(webServiceMessageSender);
                webServiceMessageSenders.add(new CamelHttpsUrlConnectionMessageSender(configuration, (HttpsUrlConnectionMessageSender) webServiceMessageSender));
            } else if (webServiceMessageSender instanceof HttpUrlConnectionMessageSender) {
                webServiceMessageSenders.remove(webServiceMessageSender);
                webServiceMessageSenders.add(new CamelHttpUrlConnectionMessageSender(configuration, (HttpUrlConnectionMessageSender) webServiceMessageSender));
            } else {
                // For example this will be the case during unit-testing with the net.javacrumbs.spring-ws-test API
                LOG.warn("Ignoring the timeout option for {} as there's no provided API available to populate it!", webServiceMessageSender);
            }
        }

        webServiceTemplate.setMessageSenders(webServiceMessageSenders.toArray(new WebServiceMessageSender[webServiceMessageSenders.size()]));
    }

    private static void setTimeOut(HttpURLConnection connection, SpringWebserviceConfiguration configuration) {
        connection.setReadTimeout(configuration.getTimeout());
    }

    private static void setTimeOut(CommonsHttpMessageSender commonsHttpMessageSender, SpringWebserviceConfiguration configuration) {
        commonsHttpMessageSender.setReadTimeout(configuration.getTimeout());
    }

    protected static class CamelHttpUrlConnectionMessageSender extends HttpUrlConnectionMessageSender {

        private final SpringWebserviceConfiguration configuration;

        CamelHttpUrlConnectionMessageSender(SpringWebserviceConfiguration configuration, HttpUrlConnectionMessageSender webServiceMessageSender) {
            this.configuration = configuration;

            // Populate the single acceptGzipEncoding property
            setAcceptGzipEncoding(webServiceMessageSender.isAcceptGzipEncoding());
        }

        @Override
        protected void prepareConnection(HttpURLConnection connection) throws IOException {
            super.prepareConnection(connection);

            setTimeOut(connection, configuration);
        }

    }

    protected static class CamelHttpsUrlConnectionMessageSender extends HttpsUrlConnectionMessageSender {

        private final SpringWebserviceConfiguration configuration;

        CamelHttpsUrlConnectionMessageSender(SpringWebserviceConfiguration configuration, final HttpsUrlConnectionMessageSender webServiceMessageSender) throws Exception {
            this.configuration = configuration;

            // Populate the single acceptGzipEncoding property beforehand as we have got a proper set/is API for it
            setAcceptGzipEncoding(webServiceMessageSender.isAcceptGzipEncoding());

            // Populate the fields not having getXXX available on HttpsUrlConnectionMessageSender
            ReflectionHelper.doWithFields(HttpsUrlConnectionMessageSender.class, new FieldCallback() {

                @Override
                public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
                    if (Modifier.isStatic(field.getModifiers())) {
                        return;
                    }

                    String fieldName = field.getName();
                    if ("logger".equals(fieldName) || "acceptGzipEncoding".equals(fieldName)) {
                        // skip them
                        return;
                    }

                    field.setAccessible(true);
                    Object value = field.get(webServiceMessageSender);
                    field.set(CamelHttpsUrlConnectionMessageSender.this, value);
                    LOG.trace("Populated the field {} with the value {}", fieldName, value);
                }

            });
        }

        @Override
        protected void prepareConnection(HttpURLConnection connection) throws IOException {
            super.prepareConnection(connection);

            setTimeOut(connection, configuration);
        }

    }

    protected static class DefaultWebserviceMessageCallback implements WebServiceMessageCallback {
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
