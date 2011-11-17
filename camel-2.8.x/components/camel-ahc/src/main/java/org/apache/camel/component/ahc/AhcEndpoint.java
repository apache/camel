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

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.util.ObjectHelper;

/**
 *
 */
public class AhcEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {

    private AsyncHttpClient client;
    private AsyncHttpClientConfig clientConfig;
    private HeaderFilterStrategy headerFilterStrategy = new HttpHeaderFilterStrategy();
    private AhcBinding binding;
    private URI httpUri;
    private boolean bridgeEndpoint;
    private boolean throwExceptionOnFailure = true;
    private boolean transferException;

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

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (client == null) {
            if (clientConfig != null) {
                client = new AsyncHttpClient(clientConfig);
            } else {
                client = new AsyncHttpClient();
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        client = null;
    }

}
