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
package org.apache.camel.component.thrift;

import java.net.URI;
import java.util.Map;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.jsse.SSLContextParameters;

@UriParams
public class ThriftConfiguration {

    @UriPath
    private String host;

    @UriPath
    @Metadata(required = true)
    private int port;

    @UriPath
    @Metadata(required = true)
    private String service;

    @UriParam(label = "producer")
    private String method;
    
    @UriParam(defaultValue = "BINARY")
    private ThriftExchangeProtocol exchangeProtocol = ThriftExchangeProtocol.BINARY;
    
    @UriParam(label = "security", defaultValue = "PLAINTEXT")
    private ThriftNegotiationType negotiationType = ThriftNegotiationType.PLAINTEXT;
    
    @UriParam(label = "security")
    private SSLContextParameters sslParameters;
    
    @UriParam(defaultValue = "NONE")
    private ThriftCompressionType compressionType = ThriftCompressionType.NONE;
    
    @UriParam(label = "consumer")
    private int clientTimeout;

    @UriParam(label = "consumer", defaultValue = "" + ThriftConstants.THRIFT_CONSUMER_POOL_SIZE)
    private int poolSize = ThriftConstants.THRIFT_CONSUMER_POOL_SIZE;

    @UriParam(label = "consumer", defaultValue = "" + ThriftConstants.THRIFT_CONSUMER_MAX_POOL_SIZE)
    private int maxPoolSize = ThriftConstants.THRIFT_CONSUMER_MAX_POOL_SIZE;

    /**
     * Fully qualified service name from the thrift descriptor file
     * (package dot service definition name)
     */
    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    /**
     * The Thrift invoked method name
     */
    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }
    
    /**
     * Exchange protocol serialization type
     */
    public ThriftExchangeProtocol getExchangeProtocol() {
        return exchangeProtocol;
    }

    public void setExchangeProtocol(ThriftExchangeProtocol exchangeProtocol) {
        this.exchangeProtocol = exchangeProtocol;
    }

    /**
     * Security negotiation type
     */
    public ThriftNegotiationType getNegotiationType() {
        return negotiationType;
    }

    public void setNegotiationType(ThriftNegotiationType negotiationType) {
        this.negotiationType = negotiationType;
    }

    /**
     * Configuration parameters for SSL/TLS security negotiation
     */
    public SSLContextParameters getSslParameters() {
        return sslParameters;
    }

    public void setSslParameters(SSLContextParameters sslParameters) {
        this.sslParameters = sslParameters;
    }
    
    /**
     * Protocol compression mechanism type
     */
    public ThriftCompressionType getCompressionType() {
        return compressionType;
    }

    public void setCompressionType(ThriftCompressionType compressionType) {
        this.compressionType = compressionType;
    }

    /**
     * The Thrift server host name. This is localhost or 0.0.0.0 (if not
     * defined) when being a consumer or remote server host name when using
     * producer.
     */
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    /**
     * The Thrift server port
     */
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
    
    /**
     * Client timeout for consumers
     */
    public int getClientTimeout() {
        return clientTimeout;
    }

    public void setClientTimeout(int clientTimeout) {
        this.clientTimeout = clientTimeout;
    }

    /**
     * The Thrift server consumer initial thread pool size
     */
    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    /**
     * The Thrift server consumer max thread pool size
     */
    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }
    
    public void parseURI(URI uri, Map<String, Object> parameters, ThriftComponent component) {
        setHost(uri.getHost());
        
        if (uri.getPort() != -1) {
            setPort(uri.getPort());
        }
        
        setService(uri.getPath().substring(1));
    }    
}
