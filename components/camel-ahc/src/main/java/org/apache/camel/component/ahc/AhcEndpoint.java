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
package org.apache.camel.component.ahc;

import java.net.URI;
import javax.net.ssl.SSLContext;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.jsse.SSLContextParameters;

@UriEndpoint(scheme = "ahc", syntax = "ahc:httpUri", producerOnly = true, label = "http")
public class AhcEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {

    private AsyncHttpClient client;
    @UriPath @Metadata(required = "true")
    private URI httpUri;
    @UriParam
    private AsyncHttpClientConfig clientConfig;
    @UriParam
    private boolean bridgeEndpoint;
    @UriParam(defaultValue = "true")
    private boolean throwExceptionOnFailure = true;
    @UriParam
    private boolean transferException;
    @UriParam
    private SSLContextParameters sslContextParameters;
    @UriParam(defaultValue = "" + 4 * 1024)
    private int bufferSize = 4 * 1024;
    @UriParam
    private HeaderFilterStrategy headerFilterStrategy = new HttpHeaderFilterStrategy();
    @UriParam
    private AhcBinding binding;

    public AhcEndpoint(String endpointUri, AhcComponent component, URI httpUri) {
        super(endpointUri, component);
        this.httpUri = httpUri;
    }

    @Override
    public AhcComponent getComponent() {
        return (AhcComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(client, "AsyncHttpClient", this);
        ObjectHelper.notNull(httpUri, "HttpUri", this);
        ObjectHelper.notNull(binding, "AhcBinding", this);
        return new AhcProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("This component does not support consuming from this endpoint");
    }

    @Override
    public boolean isLenientProperties() {
        // true to allow dynamic URI options to be configured and passed to external system for eg. the HttpProducer
        return true;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public AsyncHttpClient getClient() {
        return client;
    }

    public void setClient(AsyncHttpClient client) {
        this.client = client;
    }

    public AsyncHttpClientConfig getClientConfig() {
        return clientConfig;
    }

    public void setClientConfig(AsyncHttpClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public URI getHttpUri() {
        return httpUri;
    }

    public void setHttpUri(URI httpUri) {
        this.httpUri = httpUri;
    }

    public AhcBinding getBinding() {
        return binding;
    }

    public void setBinding(AhcBinding binding) {
        this.binding = binding;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public boolean isBridgeEndpoint() {
        return bridgeEndpoint;
    }

    public void setBridgeEndpoint(boolean bridgeEndpoint) {
        this.bridgeEndpoint = bridgeEndpoint;
    }

    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public boolean isTransferException() {
        return transferException;
    }

    public void setTransferException(boolean transferException) {
        this.transferException = transferException;
    }
    
    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (client == null) {
            
            AsyncHttpClientConfig config = null;
            
            if (clientConfig != null) {
                AsyncHttpClientConfig.Builder builder = AhcComponent.cloneConfig(clientConfig);
                
                if (sslContextParameters != null) {
                    SSLContext ssl = sslContextParameters.createSSLContext();
                    builder.setSSLContext(ssl);
                }
                
                config = builder.build();
            } else {
                if (sslContextParameters != null) {
                    AsyncHttpClientConfig.Builder builder = new AsyncHttpClientConfig.Builder();
                    SSLContext ssl = sslContextParameters.createSSLContext();
                    builder.setSSLContext(ssl);
                    config = builder.build();
                }
            }
            client = createClient(config);
        }
    }

    protected AsyncHttpClient createClient(AsyncHttpClientConfig config) {
        if (config == null) {
            return new AsyncHttpClient();
        } else {
            return new AsyncHttpClient(config);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        // ensure client is closed when stopping
        if (client != null && !client.isClosed()) {
            client.close();
        }
        client = null;
    }

}
