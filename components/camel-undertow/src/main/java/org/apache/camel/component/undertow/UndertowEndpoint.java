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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an Undertow endpoint.
 */
@UriEndpoint(scheme = "undertow", title = "Undertow", syntax = "undertow:host:port/path",
    consumerClass = UndertowConsumer.class, label = "http")
public class UndertowEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {
    private static final Logger LOG = LoggerFactory.getLogger(UndertowEndpoint.class);

    private UndertowHttpBinding undertowHttpBinding;
    private UndertowComponent component;
    private HeaderFilterStrategy headerFilterStrategy;
    private SSLContext sslContext;

    @UriPath
    private URI httpURI;
    @UriParam
    private String httpMethodRestrict;
    @UriParam
    private Boolean matchOnUriPrefix = true;
    @UriParam
    private Boolean throwExceptionOnFailure;
    @UriParam
    private Boolean transferException;

    public UndertowEndpoint(String uri, UndertowComponent component, URI httpURI) throws URISyntaxException {
        super(uri, component);
        this.component = component;
        this.httpURI = httpURI;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new UndertowProducer(this);
    }

    public Exchange createExchange(HttpServerExchange httpExchange) throws Exception {
        Exchange exchange = createExchange();

        Message in = getUndertowHttpBinding().toCamelMessage(httpExchange, exchange);

        exchange.setProperty(Exchange.CHARSET_NAME, httpExchange.getRequestCharset());
        in.setHeader(Exchange.HTTP_CHARACTER_ENCODING, httpExchange.getRequestCharset());

        exchange.setIn(in);
        return exchange;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new UndertowConsumer(this, processor);
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        //throw exception as polling consumer is not supported
        throw new UnsupportedOperationException("This component does not support polling consumer");
    }

    public boolean isSingleton() {
        return true;
    }

    public URI getHttpURI() {
        return httpURI;
    }

    /**
     * Set full HTTP URI
     */
    public void setHttpURI(URI httpURI) {
        this.httpURI = httpURI;
    }


    public String getHttpMethodRestrict() {
        return httpMethodRestrict;
    }

    /**
     * Configure set of allowed HTTP request method
     */
    public void setHttpMethodRestrict(String httpMethodRestrict) {
        this.httpMethodRestrict = httpMethodRestrict;
    }

    public Boolean getMatchOnUriPrefix() {
        return matchOnUriPrefix;
    }

    /**
     * Set if URI should be matched on prefix
     */
    public void setMatchOnUriPrefix(Boolean matchOnUriPrefix) {
        this.matchOnUriPrefix = matchOnUriPrefix;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
        undertowHttpBinding.setHeaderFilterStrategy(headerFilterStrategy);
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    public void setSslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    public Boolean getThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    /**
     * Configure if exception should be thrown on failure
     */
    public void setThrowExceptionOnFailure(Boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public Boolean getTransferException() {
        return transferException;
    }

    /**
     * Configure if exception should be transferred to client
     */
    public void setTransferException(Boolean transferException) {
        this.transferException = transferException;
    }

    public UndertowHttpBinding getUndertowHttpBinding() {
        return undertowHttpBinding;
    }

    public void setUndertowHttpBinding(UndertowHttpBinding undertowHttpBinding) {
        this.undertowHttpBinding = undertowHttpBinding;
    }

    @Override
    public UndertowComponent getComponent() {
        return component;
    }

    public void setComponent(UndertowComponent component) {
        this.component = component;
    }
}
