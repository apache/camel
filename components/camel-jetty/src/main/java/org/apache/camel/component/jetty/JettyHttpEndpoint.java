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
package org.apache.camel.component.jetty;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.Filter;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.http.HttpConsumer;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.camel.impl.SynchronousDelegateProducer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.server.Handler;

/**
 * @version 
 */
@UriEndpoint(scheme = "jetty")
public class JettyHttpEndpoint extends HttpEndpoint {

    @UriParam
    private boolean sessionSupport;
    private List<Handler> handlers;
    private HttpClient client;
    @UriParam
    private Integer httpClientMinThreads;
    @UriParam
    private Integer httpClientMaxThreads;
    private JettyHttpBinding jettyBinding;
    @UriParam
    private boolean enableJmx;
    @UriParam
    private boolean enableMultipartFilter;
    @UriParam
    private boolean sendServerVersion = true;
    @UriParam
    private boolean sendDateHeader;
    private Filter multipartFilter;
    private List<Filter> filters;
    @UriParam
    private Long continuationTimeout;
    @UriParam
    private Boolean useContinuation;
    private SSLContextParameters sslContextParameters;
    private Map<String, Object> httpClientParameters;

    public JettyHttpEndpoint(JettyHttpComponent component, String uri, URI httpURL) throws URISyntaxException {
        super(uri, component, httpURL);
    }

    @Override
    public JettyHttpComponent getComponent() {
        return (JettyHttpComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        JettyHttpProducer answer = new JettyHttpProducer(this);
        if (client != null) {
            // use shared client, and ensure its started so we can use it
            client.start();
            answer.setSharedClient(client);
        } else {
            // create a new client
            // thread pool min/max from endpoint take precedence over from component
            Integer min = httpClientMinThreads != null ? httpClientMinThreads : getComponent().getHttpClientMinThreads();
            Integer max = httpClientMaxThreads != null ? httpClientMaxThreads : getComponent().getHttpClientMaxThreads();
            HttpClient httpClient = getComponent().createHttpClient(this, min, max, sslContextParameters);

            // set optional http client parameters
            if (httpClientParameters != null) {
                // copy parameters as we need to re-use them again if creating a new producer later
                Map<String, Object> params = new HashMap<String, Object>(httpClientParameters);
                IntrospectionSupport.setProperties(httpClient, params);
                // validate we could set all parameters
                if (params.size() > 0) {
                    throw new ResolveEndpointFailedException(getEndpointUri(), "There are " + params.size()
                            + " parameters that couldn't be set on the endpoint."
                            + " Check the uri if the parameters are spelt correctly and that they are properties of the endpoint."
                            + " Unknown parameters=[" + params + "]");
                }
            }
            answer.setClient(httpClient);
        }

        answer.setBinding(getJettyBinding());
        if (isSynchronous()) {
            return new SynchronousDelegateProducer(answer);
        } else {
            return answer;
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        HttpConsumer answer = new HttpConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }   

    public void setSessionSupport(boolean support) {
        sessionSupport = support;
    }

    public boolean isSessionSupport() {
        return sessionSupport;
    }
   
    public List<Handler> getHandlers() {
        return handlers;
    }

    public void setHandlers(List<Handler> handlers) {
        this.handlers = handlers;
    }

    public HttpClient getClient() throws Exception {
        return client;
    }

    /**
     * Sets a shared {@link HttpClient} to use for all producers
     * created by this endpoint. By default each producer will
     * use a new http client, and not share.
     * <p/>
     * <b>Important: </b> Make sure to handle the lifecycle of the shared
     * client, such as stopping the client, when it is no longer in use.
     * Camel will call the <tt>start</tt> method on the client to ensure
     * its started when this endpoint creates a producer.
     * <p/>
     * This options should only be used in special circumstances.
     */
    public void setClient(HttpClient client) {
        this.client = client;
    }

    public synchronized JettyHttpBinding getJettyBinding() {
        if (jettyBinding == null) {
            jettyBinding = new DefaultJettyHttpBinding();
            jettyBinding.setHeaderFilterStrategy(getHeaderFilterStrategy());
            jettyBinding.setThrowExceptionOnFailure(isThrowExceptionOnFailure());
            jettyBinding.setTransferException(isTransferException());
        }
        return jettyBinding;
    }

    public void setJettyBinding(JettyHttpBinding jettyBinding) {
        this.jettyBinding = jettyBinding;
    }

    public boolean isEnableJmx() {
        return this.enableJmx;
    }

    public void setEnableJmx(boolean enableJmx) {
        this.enableJmx = enableJmx;
    }
    
    public boolean isSendServerVersion() {
        return sendServerVersion;
    }
    
    public void setSendServerVersion(boolean sendServerVersion) {
        this.sendServerVersion = sendServerVersion;
    }
    
    public boolean isSendDateHeader() { 
        return sendDateHeader;
    }
    
    public void setSendDateHeader(boolean sendDateHeader) { 
        this.sendDateHeader = sendDateHeader;
    }
    
    public boolean isEnableMultipartFilter() {
        return enableMultipartFilter;
    }

    public void setEnableMultipartFilter(boolean enableMultipartFilter) {
        this.enableMultipartFilter = enableMultipartFilter;
    }
    
    public void setMultipartFilter(Filter filter) {
        this.multipartFilter = filter;
    }
    
    public Filter getMultipartFilter() {
        return multipartFilter;
    }
    
    public void setFilters(List<Filter> filterList) {
        this.filters = filterList;
    }
    
    public List<Filter> getFilters() {
        return filters;
    }

    public Long getContinuationTimeout() {
        return continuationTimeout;
    }

    public void setContinuationTimeout(Long continuationTimeout) {
        this.continuationTimeout = continuationTimeout;
    }

    public Boolean getUseContinuation() {
        return useContinuation;
    }

    public void setUseContinuation(Boolean useContinuation) {
        this.useContinuation = useContinuation;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public Integer getHttpClientMinThreads() {
        return httpClientMinThreads;
    }

    public void setHttpClientMinThreads(Integer httpClientMinThreads) {
        this.httpClientMinThreads = httpClientMinThreads;
    }

    public Integer getHttpClientMaxThreads() {
        return httpClientMaxThreads;
    }

    public void setHttpClientMaxThreads(Integer httpClientMaxThreads) {
        this.httpClientMaxThreads = httpClientMaxThreads;
    }

    public Map<String, Object> getHttpClientParameters() {
        return httpClientParameters;
    }

    public void setHttpClientParameters(Map<String, Object> httpClientParameters) {
        this.httpClientParameters = httpClientParameters;
    }

}
