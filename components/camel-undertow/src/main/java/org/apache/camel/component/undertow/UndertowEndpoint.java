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
package org.apache.camel.component.undertow;

import java.net.URI;
import java.net.URISyntaxException;
import javax.net.ssl.SSLContext;

import io.undertow.server.HttpServerExchange;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.jsse.SSLContextParameters;

/**
 * Represents an Undertow endpoint.
 */
@UriEndpoint(scheme = "undertow", title = "Undertow", syntax = "undertow:httpURI",
    consumerClass = UndertowConsumer.class, label = "http")
public class UndertowEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {

    private UndertowComponent component;
    private SSLContext sslContext;

    @UriPath
    private URI httpURI;
    @UriParam
    private UndertowHttpBinding undertowHttpBinding;
    @UriParam
    private HeaderFilterStrategy headerFilterStrategy;
    @UriParam
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "consumer")
    private String httpMethodRestrict;
    @UriParam(label = "consumer", defaultValue = "true")
    private Boolean matchOnUriPrefix = true;
    @UriParam(label = "producer")
    private Boolean throwExceptionOnFailure;
    @UriParam
    private Boolean transferException;

    public UndertowEndpoint(String uri, UndertowComponent component) throws URISyntaxException {
        super(uri, component);
        this.component = component;
    }

    @Override
    public UndertowComponent getComponent() {
        return component;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new UndertowProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new UndertowConsumer(this, processor);
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        //throw exception as polling consumer is not supported
        throw new UnsupportedOperationException("This component does not support polling consumer");
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public boolean isLenientProperties() {
        // true to allow dynamic URI options to be configured and passed to external system for eg. the UndertowProducer
        return true;
    }

    public Exchange createExchange(HttpServerExchange httpExchange) throws Exception {
        Exchange exchange = createExchange();

        Message in = getUndertowHttpBinding().toCamelMessage(httpExchange, exchange);

        exchange.setProperty(Exchange.CHARSET_NAME, httpExchange.getRequestCharset());
        in.setHeader(Exchange.HTTP_CHARACTER_ENCODING, httpExchange.getRequestCharset());

        exchange.setIn(in);
        return exchange;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    public URI getHttpURI() {
        return httpURI;
    }

    /**
     * The url of the HTTP endpoint to use.
     */
    public void setHttpURI(URI httpURI) {
        this.httpURI = httpURI;
    }

    public String getHttpMethodRestrict() {
        return httpMethodRestrict;
    }

    /**
     * Used to only allow consuming if the HttpMethod matches, such as GET/POST/PUT etc. Multiple methods can be specified separated by comma.
     */
    public void setHttpMethodRestrict(String httpMethodRestrict) {
        this.httpMethodRestrict = httpMethodRestrict;
    }

    public Boolean getMatchOnUriPrefix() {
        return matchOnUriPrefix;
    }

    /**
     * Whether or not the consumer should try to find a target consumer by matching the URI prefix if no exact match is found.
     */
    public void setMatchOnUriPrefix(Boolean matchOnUriPrefix) {
        this.matchOnUriPrefix = matchOnUriPrefix;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
     */
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
        undertowHttpBinding.setHeaderFilterStrategy(headerFilterStrategy);
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * To configure security using SSLContextParameters
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public Boolean getThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    /**
     * If the option is true, HttpProducer will ignore the Exchange.HTTP_URI header, and use the endpoint's URI for request.
     * You may also set the option throwExceptionOnFailure to be false to let the producer send all the fault response back.
     */
    public void setThrowExceptionOnFailure(Boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public Boolean getTransferException() {
        return transferException;
    }

    /**
     * Option to disable throwing the HttpOperationFailedException in case of failed responses from the remote server.
     * This allows you to get all responses regardless of the HTTP status code.
     */
    public void setTransferException(Boolean transferException) {
        this.transferException = transferException;
    }

    public UndertowHttpBinding getUndertowHttpBinding() {
        return undertowHttpBinding;
    }

    /**
     * To use a custom UndertowHttpBinding to control the mapping between Camel message and undertow.
     */
    public void setUndertowHttpBinding(UndertowHttpBinding undertowHttpBinding) {
        this.undertowHttpBinding = undertowHttpBinding;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (sslContextParameters != null) {
            sslContext = sslContextParameters.createSSLContext();
        }
    }
}
