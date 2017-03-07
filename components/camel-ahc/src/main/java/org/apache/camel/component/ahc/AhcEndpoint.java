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
import java.util.Map;
import javax.net.ssl.SSLContext;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.JdkSslContext;

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.http.common.cookie.CookieHandler;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;

/**
 * To call external HTTP services using <a href="http://github.com/sonatype/async-http-client">Async Http Client</a>.
 */
@UriEndpoint(firstVersion = "2.8.0", scheme = "ahc", title = "AHC", syntax = "ahc:httpUri", producerOnly = true, label = "http", lenientProperties = true)
public class AhcEndpoint extends DefaultEndpoint implements AsyncEndpoint, HeaderFilterStrategyAware {

    private AsyncHttpClient client;
    @UriPath @Metadata(required = "true")
    private URI httpUri;
    @UriParam
    private boolean bridgeEndpoint;
    @UriParam(defaultValue = "true")
    private boolean throwExceptionOnFailure = true;
    @UriParam
    private boolean transferException;
    @UriParam(defaultValue = "" + 4 * 1024)
    private int bufferSize = 4 * 1024;
    @UriParam
    private HeaderFilterStrategy headerFilterStrategy = new HttpHeaderFilterStrategy();
    @UriParam(label = "advanced")
    private AhcBinding binding;
    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "advanced")
    private AsyncHttpClientConfig clientConfig;
    @UriParam(label = "advanced", prefix = "clientConfig.", multiValue = true)
    private Map<String, Object> clientConfigOptions;
    @UriParam(label = "advanced,security", prefix = "clientConfig.realm.", multiValue = true)
    private Map<String, Object> clientConfigRealmOptions;
    @UriParam(label = "producer", defaultValue = "false")
    private boolean connectionClose;
    @UriParam(label = "producer")
    private CookieHandler cookieHandler;

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

    /**
     * To use a custom {@link AsyncHttpClient}
     */
    public void setClient(AsyncHttpClient client) {
        this.client = client;
    }

    public AsyncHttpClientConfig getClientConfig() {
        return clientConfig;
    }

    /**
     * To configure the AsyncHttpClient to use a custom com.ning.http.client.AsyncHttpClientConfig instance.
     */
    public void setClientConfig(AsyncHttpClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public URI getHttpUri() {
        return httpUri;
    }

    /**
     * The URI to use such as http://hostname:port/path
     */
    public void setHttpUri(URI httpUri) {
        this.httpUri = httpUri;
    }

    public AhcBinding getBinding() {
        return binding;
    }

    /**
     * To use a custom {@link AhcBinding} which allows to control how to bind between AHC and Camel.
     */
    public void setBinding(AhcBinding binding) {
        this.binding = binding;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
     */
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public boolean isBridgeEndpoint() {
        return bridgeEndpoint;
    }

    /**
     * If the option is true, then the Exchange.HTTP_URI header is ignored, and use the endpoint's URI for request.
     * You may also set the throwExceptionOnFailure to be false to let the AhcProducer send all the fault response back.
     */
    public void setBridgeEndpoint(boolean bridgeEndpoint) {
        this.bridgeEndpoint = bridgeEndpoint;
    }

    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    /**
     * Option to disable throwing the AhcOperationFailedException in case of failed responses from the remote server.
     * This allows you to get all responses regardless of the HTTP status code.
     */
    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public boolean isTransferException() {
        return transferException;
    }

    /**
     * If enabled and an Exchange failed processing on the consumer side, and if the caused Exception was send back serialized
     * in the response as a application/x-java-serialized-object content type (for example using Jetty or Servlet Camel components).
     * On the producer side the exception will be deserialized and thrown as is, instead of the AhcOperationFailedException.
     * The caused exception is required to be serialized.
     * <p/>
     * This is by default turned off. If you enable this then be aware that Java will deserialize the incoming
     * data from the request to Java and that can be a potential security risk.
     */
    public void setTransferException(boolean transferException) {
        this.transferException = transferException;
    }
    
    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * Reference to a org.apache.camel.util.jsse.SSLContextParameters in the Registry.
     * This reference overrides any configured SSLContextParameters at the component level.
     * See Using the JSSE Configuration Utility.
     * Note that configuring this option will override any SSL/TLS configuration options provided through the clientConfig option at the endpoint or component level.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * The initial in-memory buffer size used when transferring data between Camel and AHC Client.
     */
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public Map<String, Object> getClientConfigOptions() {
        return clientConfigOptions;
    }

    /**
     * To configure the AsyncHttpClientConfig using the key/values from the Map.
     */
    public void setClientConfigOptions(Map<String, Object> clientConfigOptions) {
        this.clientConfigOptions = clientConfigOptions;
    }

    public Map<String, Object> getClientConfigRealmOptions() {
        return clientConfigRealmOptions;
    }

    /**
     * To configure the AsyncHttpClientConfig Realm using the key/values from the Map.
     */
    public void setClientConfigRealmOptions(Map<String, Object> clientConfigRealmOptions) {
        this.clientConfigRealmOptions = clientConfigRealmOptions;
    }

    public boolean isConnectionClose() {
        return connectionClose;
    }
    
    /**
     * Define if the Connection Close header has to be added to HTTP Request. This parameter is false by default
     */
    public void setConnectionClose(boolean connectionClose) {
        this.connectionClose = connectionClose;
    }

    public CookieHandler getCookieHandler() {
        return cookieHandler;
    }

    /**
     * Configure a cookie handler to maintain a HTTP session
     */
    public void setCookieHandler(CookieHandler cookieHandler) {
        this.cookieHandler = cookieHandler;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (client == null) {
            
            AsyncHttpClientConfig config = null;
            
            if (clientConfig != null) {
                DefaultAsyncHttpClientConfig.Builder builder = AhcComponent.cloneConfig(clientConfig);
                
                if (sslContextParameters != null) {
                    SSLContext sslContext = sslContextParameters.createSSLContext(getCamelContext());
                    JdkSslContext ssl = new JdkSslContext(sslContext, true, ClientAuth.REQUIRE);
                    builder.setSslContext(ssl);
                }
                
                config = builder.build();
            } else {
                if (sslContextParameters != null) {
                    DefaultAsyncHttpClientConfig.Builder builder = new DefaultAsyncHttpClientConfig.Builder();
                    SSLContext sslContext = sslContextParameters.createSSLContext(getCamelContext());
                    JdkSslContext ssl = new JdkSslContext(sslContext, true, ClientAuth.REQUIRE);
                    builder.setSslContext(ssl);
                    config = builder.build();
                }
            }
            client = createClient(config);
        }
    }

    protected AsyncHttpClient createClient(AsyncHttpClientConfig config) {
        if (config == null) {
            return new DefaultAsyncHttpClient();
        } else {
            return new DefaultAsyncHttpClient(config);
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
