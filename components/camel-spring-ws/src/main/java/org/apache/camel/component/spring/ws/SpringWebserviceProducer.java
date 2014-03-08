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
import java.security.GeneralSecurityException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ExchangeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.core.SourceExtractor;
import org.springframework.ws.client.core.WebServiceMessageCallback;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.addressing.client.ActionCallback;
import org.springframework.ws.soap.addressing.core.EndpointReference;
import org.springframework.ws.soap.client.core.SoapActionCallback;
import org.springframework.ws.transport.WebServiceConnection;
import org.springframework.ws.transport.WebServiceMessageSender;
import org.springframework.ws.transport.http.AbstractHttpWebServiceMessageSender;
import org.springframework.ws.transport.http.HttpComponentsMessageSender;
import org.springframework.ws.transport.http.HttpUrlConnection;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;

public class SpringWebserviceProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SpringWebserviceProducer.class);
    private static final SourceExtractor<Object> SOURCE_EXTRACTOR = new NoopSourceExtractor();
    private static final XmlConverter XML_CONVERTER = new XmlConverter();

    public SpringWebserviceProducer(Endpoint endpoint) {
        super(endpoint);
        prepareMessageSenders(getEndpoint().getConfiguration());
    }

    @Override
    public SpringWebserviceEndpoint getEndpoint() {
        return (SpringWebserviceEndpoint) super.getEndpoint();
    }

    public void process(Exchange exchange) throws Exception {
        // Let Camel TypeConverter hierarchy handle the conversion of XML messages to Source objects
        Source sourcePayload = exchange.getIn().getMandatoryBody(Source.class);

        // Extract optional headers
        String endpointUriHeader = exchange.getIn().getHeader(SpringWebserviceConstants.SPRING_WS_ENDPOINT_URI, String.class);
        String soapActionHeader = exchange.getIn().getHeader(SpringWebserviceConstants.SPRING_WS_SOAP_ACTION, String.class);
        URI wsAddressingActionHeader = exchange.getIn().getHeader(SpringWebserviceConstants.SPRING_WS_ADDRESSING_ACTION, URI.class);
        URI wsReplyToHeader = exchange.getIn().getHeader(SpringWebserviceConstants.SPRING_WS_ADDRESSING_PRODUCER_REPLY_TO, URI.class);
        URI wsFaultToHeader = exchange.getIn().getHeader(SpringWebserviceConstants.SPRING_WS_ADDRESSING_PRODUCER_FAULT_TO, URI.class);
        Source soapHeaderSource = exchange.getIn().getHeader(SpringWebserviceConstants.SPRING_WS_SOAP_HEADER, Source.class);

        WebServiceMessageCallback callback = new DefaultWebserviceMessageCallback(soapActionHeader, wsAddressingActionHeader,
                wsReplyToHeader, wsFaultToHeader, soapHeaderSource, getEndpoint().getConfiguration(), exchange);

        Object body;
        if (endpointUriHeader != null) {
            body = getEndpoint().getConfiguration().getWebServiceTemplate().sendSourceAndReceive(endpointUriHeader, sourcePayload, callback, SOURCE_EXTRACTOR);
        } else {
            body = getEndpoint().getConfiguration().getWebServiceTemplate().sendSourceAndReceive(sourcePayload, callback, SOURCE_EXTRACTOR);
        }

        if (ExchangeHelper.isOutCapable(exchange)) {
            exchange.getOut().copyFrom(exchange.getIn());
            exchange.getOut().setBody(body);
        } else {
            exchange.getIn().setBody(body);
        }
    }

    private static void prepareMessageSenders(SpringWebserviceConfiguration configuration) {
        // Skip this whole thing if none of the relevant config options are set.
        if (!(configuration.getTimeout() > -1) && configuration.getSslContextParameters() == null) {
            return;
        }

        WebServiceTemplate webServiceTemplate = configuration.getWebServiceTemplate();

        WebServiceMessageSender[] messageSenders = webServiceTemplate.getMessageSenders();

        for (int i = 0; i < messageSenders.length; i++) {
            WebServiceMessageSender messageSender = messageSenders[i];
            if (messageSender instanceof HttpComponentsMessageSender) {
                if (configuration.getSslContextParameters() != null) {
                    LOG.warn("Not applying SSLContextParameters based configuration to HttpComponentsMessageSender.  "
                            + "If you are using this MessageSender, which you are not by default, you will need "
                            + "to configure SSL using the Commons HTTP 3.x Protocol registry.");
                }

                if (configuration.getTimeout() > -1) {
                    if (messageSender.getClass().equals(HttpComponentsMessageSender.class)) {
                        ((HttpComponentsMessageSender) messageSender).setReadTimeout(configuration.getTimeout());
                    } else {
                        LOG.warn("Not applying timeout configuration to HttpComponentsMessageSender based implementation.  "
                                + "You are using what appears to be a custom MessageSender, which you are not doing by default. "
                                + "You will need configure timeout on your own.");
                    }
                }
            } else if (messageSender.getClass().equals(HttpUrlConnectionMessageSender.class)) {
                // Only if exact match denoting likely use of default configuration.  We don't want to get
                // sub-classes that might have been otherwise injected.
                messageSenders[i] = new AbstractHttpWebServiceMessageSenderDecorator((HttpUrlConnectionMessageSender) messageSender, configuration);
            } else {
                // For example this will be the case during unit-testing with the net.javacrumbs.spring-ws-test API
                LOG.warn("Ignoring the timeout and SSLContextParameters options for {}.  You will need to configure "
                        + "these options directly on your custom configured WebServiceMessageSender", messageSender);
            }
        }
    }

    /**
     * A decorator of {@link HttpUrlConnectionMessageSender} instances that can apply configuration options
     * from the Camel component/endpoint configuration without replacing the actual implementation which may
     * actually be an end-user implementation and not one of the built-in implementations.
     */
    protected static final class AbstractHttpWebServiceMessageSenderDecorator extends AbstractHttpWebServiceMessageSender {

        private final AbstractHttpWebServiceMessageSender delegate;

        private final SpringWebserviceConfiguration configuration;

        private SSLContext sslContext;

        public AbstractHttpWebServiceMessageSenderDecorator(AbstractHttpWebServiceMessageSender delegate, SpringWebserviceConfiguration configuration) {
            this.delegate = delegate;
            this.configuration = configuration;
        }

        @Override
        public WebServiceConnection createConnection(URI uri) throws IOException {
            WebServiceConnection wsc = delegate.createConnection(uri);
            if (wsc instanceof HttpUrlConnection) {
                HttpURLConnection connection = ((HttpUrlConnection) wsc).getConnection();

                if (configuration.getTimeout() > -1) {
                    connection.setReadTimeout(configuration.getTimeout());
                }

                if (configuration.getSslContextParameters() != null && connection instanceof HttpsURLConnection) {
                    try {
                        synchronized (this) {
                            if (sslContext == null) {
                                sslContext = configuration.getSslContextParameters().createSSLContext();
                            }
                        }
                    } catch (GeneralSecurityException e) {
                        throw new RuntimeCamelException("Error creating SSLContext based on SSLContextParameters.", e);
                    }

                    ((HttpsURLConnection) connection).setSSLSocketFactory(sslContext.getSocketFactory());
                }
            } else {
                throw new RuntimeCamelException("Unsupported delegate.  Delegate must return a org.springframework.ws.transport.http.HttpUrlConnection.  Found "
                        + wsc.getClass());
            }

            return wsc;
        }

        @Override
        public boolean isAcceptGzipEncoding() {
            return delegate.isAcceptGzipEncoding();
        }

        @Override
        public void setAcceptGzipEncoding(boolean acceptGzipEncoding) {
            delegate.setAcceptGzipEncoding(acceptGzipEncoding);
        }

        @Override
        public boolean supports(URI uri) {
            return delegate.supports(uri);
        }
    }

    protected static class DefaultWebserviceMessageCallback implements WebServiceMessageCallback {
        private final String soapActionHeader;
        private final URI wsAddressingActionHeader;
        private final URI wsReplyToHeader;
        private final URI wsFaultToHeader;
        private final Source soapHeaderSource;
        private final SpringWebserviceConfiguration configuration;
        private final Exchange exchange;

        public DefaultWebserviceMessageCallback(String soapAction, URI wsAddressingAction, URI wsReplyTo, URI wsFaultTo, Source soapHeaderSource,
                                                SpringWebserviceConfiguration configuration, Exchange exchange) {
            this.soapActionHeader = soapAction;
            this.wsAddressingActionHeader = wsAddressingAction;
            this.wsReplyToHeader = wsReplyTo;
            this.wsFaultToHeader = wsFaultTo;
            this.soapHeaderSource = soapHeaderSource;
            this.configuration = configuration;
            this.exchange = exchange;
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
            URI wsReplyTo = wsReplyToHeader != null ? wsReplyToHeader : configuration.getReplyTo();
            URI wsFaultTo = wsFaultToHeader != null ? wsFaultToHeader : configuration.getFaultTo();

            // Create the SOAP header
            if (soapHeaderSource != null) {
                SoapHeader header = ((SoapMessage) message).getSoapHeader();
                XML_CONVERTER.toResult(soapHeaderSource, header.getResult());
            }

            if (wsAddressingAction != null) {
                ActionCallback actionCallback = new ActionCallback(wsAddressingAction);
                if (wsReplyTo != null) {
                    actionCallback.setReplyTo(new EndpointReference(wsReplyTo));
                }

                if (wsFaultTo != null) {
                    actionCallback.setFaultTo(new EndpointReference(wsFaultTo));
                }
                actionCallback.doWithMessage(message);
            }

            configuration.getMessageFilter().filterProducer(exchange, message);
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
