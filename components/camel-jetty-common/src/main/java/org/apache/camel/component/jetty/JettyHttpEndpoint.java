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
import org.apache.camel.http.common.HttpCommonEndpoint;
import org.apache.camel.http.common.HttpConsumer;
import org.apache.camel.http.common.cookie.CookieHandler;
import org.apache.camel.impl.SynchronousDelegateProducer;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.server.Handler;

/**
 * @version 
 */
public abstract class JettyHttpEndpoint extends HttpCommonEndpoint {

    @UriParam(label = "producer,advanced",
            description = "Sets a shared HttpClient to use for all producers created by this endpoint. By default each producer will"
                    + " use a new http client, and not share. Important: Make sure to handle the lifecycle of the shared"
                    + " client, such as stopping the client, when it is no longer in use. Camel will call the start method on the client to ensure"
                    + " its started when this endpoint creates a producer. This options should only be used in special circumstances.")
    private HttpClient httpClient;
    @UriParam(label = "consumer",
            description = "Specifies whether to enable the session manager on the server side of Jetty.")
    private boolean sessionSupport;
    @UriParam(label = "producer", defaultValue = "8",
            description = "To set a value for minimum number of threads in HttpClient thread pool."
                    + " This setting override any setting configured on component level."
                    + " Notice that both a min and max size must be configured. If not set it default to min 8 threads used in Jettys thread pool.")
    private Integer httpClientMinThreads;
    @UriParam(label = "producer", defaultValue = "254",
            description = "To set a value for maximum number of threads in HttpClient thread pool."
                    + " This setting override any setting configured on component level."
                    + " Notice that both a min and max size must be configured. If not set it default to max 254 threads used in Jettys thread pool.")
    private Integer httpClientMaxThreads;
    @UriParam(label = "consumer",
            description = "If this option is true, Jetty JMX support will be enabled for this endpoint. See Jetty JMX support for more details.")
    private boolean enableJmx;
    @UriParam(description = "Whether Jetty org.eclipse.jetty.servlets.MultiPartFilter is enabled or not."
            + " You should set this value to false when bridging endpoints, to ensure multipart requests is proxied/bridged as well.")
    private boolean enableMultipartFilter;
    @UriParam(label = "consumer", defaultValue = "true",
            description = "If the option is true, jetty will send the server header with the jetty version information to the client which sends the request."
                    + " NOTE please make sure there is no any other camel-jetty endpoint is share the same port, otherwise this option may not work as expected.")
    private boolean sendServerVersion = true;
    @UriParam(label = "consumer", description = "If the option is true, jetty server will send the date header to the client which sends the request."
            + " NOTE please make sure there is no any other camel-jetty endpoint is share the same port, otherwise this option may not work as expected.")
    private boolean sendDateHeader;
    @UriParam(label = "consumer", defaultValue = "30000",
            description = "Allows to set a timeout in millis when using Jetty as consumer (server)."
            + " By default Jetty uses 30000. You can use a value of <= 0 to never expire."
            + " If a timeout occurs then the request will be expired and Jetty will return back a http error 503 to the client."
            + " This option is only in use when using Jetty with the Asynchronous Routing Engine.")
    private Long continuationTimeout;
    @UriParam(label = "consumer",
            description = "Whether or not to use Jetty continuations for the Jetty Server.")
    private Boolean useContinuation;
    @UriParam(label = "consumer",
            description = "If the option is true, Jetty server will setup the CrossOriginFilter which supports the CORS out of box.")
    private boolean enableCORS;
    @UriParam(label = "producer,advanced", prefix = "httpClient.", multiValue = true,
            description = "Configuration of Jetty's HttpClient. For example, setting httpClient.idleTimeout=30000 sets the idle timeout to 30 seconds."
            + " And httpClient.timeout=30000 sets the request timeout to 30 seconds, in case you want to timeout sooner if you have long running request/response calls.")
    private Map<String, Object> httpClientParameters;
    @UriParam(label = "consumer,advanced", javaType = "java.lang.String",
            description = "Specifies a comma-delimited set of Handler instances to lookup in your Registry."
            + " These handlers are added to the Jetty servlet context (for example, to add security)."
            + " Important: You can not use different handlers with different Jetty endpoints using the same port number."
            + " The handlers is associated to the port number. If you need different handlers, then use different port numbers.")
    private List<Handler> handlers;
    @UriParam(label = "consumer,advanced", javaType = "java.lang.String", name = "filtersRef",
            description = "Allows using a custom filters which is putted into a list and can be find in the Registry."
            + " Multiple values can be separated by comma.")
    private List<Filter> filters;
    @UriParam(label = "consumer,advanced", prefix = "filter.", multiValue = true,
            description = "Configuration of the filter init parameters. These parameters will be applied to the filter list before starting the jetty server.")
    private Map<String, String> filterInitParameters;

    @UriParam(label = "producer,advanced",
        description = "To use a custom JettyHttpBinding which be used to customize how a response should be written for the producer.")
    private JettyHttpBinding jettyBinding;
    @UriParam(label = "producer,advanced",
            description = "To use a custom JettyHttpBinding which be used to customize how a response should be written for the producer.")
    @Deprecated
    private String jettyBindingRef;
    @UriParam(label = "consumer,advanced",
            description = "Option to disable throwing the HttpOperationFailedException in case of failed responses from the remote server."
            + " This allows you to get all responses regardless of the HTTP status code.")
    @Deprecated
    private String httpBindingRef;
    @UriParam(label = "consumer,advanced",
            description = "Allows using a custom multipart filter. Note: setting multipartFilterRef forces the value of enableMultipartFilter to true.")
    private Filter multipartFilter;
    @UriParam(label = "consumer,advanced",
            description = "Allows using a custom multipart filter. Note: setting multipartFilterRef forces the value of enableMultipartFilter to true.")
    @Deprecated
    private String multipartFilterRef;
    @UriParam(label = "security",
            description = "To configure security using SSLContextParameters")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "producer", description = "Configure a cookie handler to maintain a HTTP session")
    private CookieHandler cookieHandler;

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
        if (httpClient != null) {
            // use shared client, and ensure its started so we can use it
            httpClient.start();
            answer.setSharedClient(httpClient);
            answer.setBinding(getJettyBinding(httpClient));
        } else {
            HttpClient httpClient = createJettyHttpClient();
            answer.setClient(httpClient);
            answer.setBinding(getJettyBinding(httpClient));
        }

        if (isSynchronous()) {
            return new SynchronousDelegateProducer(answer);
        } else {
            return answer;
        }
    }

    protected HttpClient createJettyHttpClient() throws Exception {
        // create a new client
        // thread pool min/max from endpoint take precedence over from component
        Integer min = httpClientMinThreads != null ? httpClientMinThreads : getComponent().getHttpClientMinThreads();
        Integer max = httpClientMaxThreads != null ? httpClientMaxThreads : getComponent().getHttpClientMaxThreads();
        HttpClient httpClient = getComponent().createHttpClient(this, min, max, sslContextParameters);

        // set optional http client parameters
        if (httpClientParameters != null) {
            // copy parameters as we need to re-use them again if creating a new producer later
            Map<String, Object> params = new HashMap<String, Object>(httpClientParameters);
            // Can not be set on httpClient for jetty 9
            params.remove("timeout");
            IntrospectionSupport.setProperties(httpClient, params);
            // validate we could set all parameters
            if (params.size() > 0) {
                throw new ResolveEndpointFailedException(getEndpointUri(), "There are " + params.size()
                        + " parameters that couldn't be set on the endpoint."
                        + " Check the uri if the parameters are spelt correctly and that they are properties of the endpoint."
                        + " Unknown parameters=[" + params + "]");
            }
        }
        return httpClient;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        HttpConsumer answer = new HttpConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    /**
     * Specifies whether to enable the session manager on the server side of Jetty.
     */
    public void setSessionSupport(boolean support) {
        sessionSupport = support;
    }

    public boolean isSessionSupport() {
        return sessionSupport;
    }
   
    public List<Handler> getHandlers() {
        return handlers;
    }

    /**
     * Specifies a comma-delimited set of org.mortbay.jetty.Handler instances in your Registry (such as your Spring ApplicationContext).
     * These handlers are added to the Jetty servlet context (for example, to add security).
     * Important: You can not use different handlers with different Jetty endpoints using the same port number.
     * The handlers is associated to the port number. If you need different handlers, then use different port numbers.
     */
    public void setHandlers(List<Handler> handlers) {
        this.handlers = handlers;
    }

    public HttpClient getHttpClient() throws Exception {
        return httpClient;
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
    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public synchronized JettyHttpBinding getJettyBinding(HttpClient httpClient) {
        if (jettyBinding == null) {
            jettyBinding = new DefaultJettyHttpBinding();
            jettyBinding.setHeaderFilterStrategy(getHeaderFilterStrategy());
            jettyBinding.setThrowExceptionOnFailure(isThrowExceptionOnFailure());
            jettyBinding.setTransferException(isTransferException());
            if (getComponent() != null) {
                jettyBinding.setAllowJavaSerializedObject(getComponent().isAllowJavaSerializedObject());
            }
            jettyBinding.setOkStatusCodeRange(getOkStatusCodeRange());
        }
        return jettyBinding;
    }

    /**
     * To use a custom JettyHttpBinding which be used to customize how a response should be written for the producer.
     */
    public void setJettyBinding(JettyHttpBinding jettyBinding) {
        this.jettyBinding = jettyBinding;
    }

    public boolean isEnableJmx() {
        return this.enableJmx;
    }

    /**
     * If this option is true, Jetty JMX support will be enabled for this endpoint. See Jetty JMX support for more details.
     */
    public void setEnableJmx(boolean enableJmx) {
        this.enableJmx = enableJmx;
    }
    
    public boolean isSendServerVersion() {
        return sendServerVersion;
    }

    /**
     * If the option is true, jetty will send the server header with the jetty version information to the client which sends the request.
     * NOTE please make sure there is no any other camel-jetty endpoint is share the same port, otherwise this option may not work as expected.
     */
    public void setSendServerVersion(boolean sendServerVersion) {
        this.sendServerVersion = sendServerVersion;
    }
    
    public boolean isSendDateHeader() { 
        return sendDateHeader;
    }

    /**
     * If the option is true, jetty server will send the date header to the client which sends the request.
     * NOTE please make sure there is no any other camel-jetty endpoint is share the same port, otherwise this option may not work as expected.
     */
    public void setSendDateHeader(boolean sendDateHeader) { 
        this.sendDateHeader = sendDateHeader;
    }
    
    public boolean isEnableMultipartFilter() {
        return enableMultipartFilter;
    }

    /**
     * Whether Jetty org.eclipse.jetty.servlets.MultiPartFilter is enabled or not.
     * You should set this value to false when bridging endpoints, to ensure multipart requests is proxied/bridged as well.
     */
    public void setEnableMultipartFilter(boolean enableMultipartFilter) {
        this.enableMultipartFilter = enableMultipartFilter;
    }

    /**
     * Allows using a custom multipart filter. Note: setting multipartFilter forces the value of enableMultipartFilter to true.
     */
    public void setMultipartFilter(Filter filter) {
        this.multipartFilter = filter;
    }
    
    public Filter getMultipartFilter() {
        return multipartFilter;
    }

    /**
     * Allows using a custom filters which is putted into a list and can be find in the Registry.
     * Multiple values can be separated by comma.
     */
    public void setFilters(List<Filter> filterList) {
        this.filters = filterList;
    }
    
    public List<Filter> getFilters() {
        return filters;
    }

    public Long getContinuationTimeout() {
        return continuationTimeout;
    }

    /**
     * Allows to set a timeout in millis when using Jetty as consumer (server).
     * By default Jetty uses 30000. You can use a value of <= 0 to never expire.
     * If a timeout occurs then the request will be expired and Jetty will return back a http error 503 to the client.
     * This option is only in use when using Jetty with the Asynchronous Routing Engine.
     */
    public void setContinuationTimeout(Long continuationTimeout) {
        this.continuationTimeout = continuationTimeout;
    }

    public Boolean getUseContinuation() {
        return useContinuation;
    }

    /**
     * Whether or not to use Jetty continuations for the Jetty Server.
     */
    public void setUseContinuation(Boolean useContinuation) {
        this.useContinuation = useContinuation;
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

    public Integer getHttpClientMinThreads() {
        return httpClientMinThreads;
    }

    /**
     * To set a value for minimum number of threads in HttpClient thread pool.
     * This setting override any setting configured on component level.
     * Notice that both a min and max size must be configured. If not set it default to min 8 threads used in Jettys thread pool.
     */
    public void setHttpClientMinThreads(Integer httpClientMinThreads) {
        this.httpClientMinThreads = httpClientMinThreads;
    }

    public Integer getHttpClientMaxThreads() {
        return httpClientMaxThreads;
    }

    /**
     * To set a value for maximum number of threads in HttpClient thread pool.
     * This setting override any setting configured on component level.
     * Notice that both a min and max size must be configured. If not set it default to max 254 threads used in Jettys thread pool.
     */
    public void setHttpClientMaxThreads(Integer httpClientMaxThreads) {
        this.httpClientMaxThreads = httpClientMaxThreads;
    }

    public Map<String, Object> getHttpClientParameters() {
        return httpClientParameters;
    }

    /**
     * Configuration of Jetty's HttpClient. For example, setting httpClient.idleTimeout=30000 sets the idle timeout to 30 seconds.
     * And httpClient.timeout=30000 sets the request timeout to 30 seconds, in case you want to timeout sooner if you have long running request/response calls.
     */
    public void setHttpClientParameters(Map<String, Object> httpClientParameters) {
        this.httpClientParameters = httpClientParameters;
    }

    public Map<String, String> getFilterInitParameters() {
        return filterInitParameters;
    }

    /**
     *  Configuration of the filter init parameters. These parameters will be applied to the filter list before starting the jetty server.
     */
    public void setFilterInitParameters(Map<String, String> filterInitParameters) {
        this.filterInitParameters = filterInitParameters;
    }

    public boolean isEnableCORS() {
        return enableCORS;
    }

    /**
     * If the option is true, Jetty server will setup the CrossOriginFilter which supports the CORS out of box.
     */
    public void setEnableCORS(boolean enableCORS) {
        this.enableCORS = enableCORS;
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

    public abstract JettyContentExchange createContentExchange();

}
