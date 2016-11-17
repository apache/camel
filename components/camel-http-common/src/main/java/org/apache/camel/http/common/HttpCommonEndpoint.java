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
package org.apache.camel.http.common;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.http.common.cookie.CookieHandler;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

public abstract class HttpCommonEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {

    // Note: all options must be documented with description in annotations so extended components can access the documentation

    HttpCommonComponent component;

    @UriPath(label = "producer", description = "The url of the HTTP endpoint to call.") @Metadata(required = "true")
    URI httpUri;
    @UriParam(description = "To use a custom HeaderFilterStrategy to filter header to and from Camel message.")
    HeaderFilterStrategy headerFilterStrategy = new HttpHeaderFilterStrategy();
    @UriParam(description = "To use a custom HttpBinding to control the mapping between Camel message and HttpClient.")
    HttpBinding httpBinding;
    @UriParam(label = "producer", defaultValue = "true",
            description = "Option to disable throwing the HttpOperationFailedException in case of failed responses from the remote server."
                    + " This allows you to get all responses regardless of the HTTP status code.")
    boolean throwExceptionOnFailure = true;
    @UriParam(label = "producer",
            description = "If the option is true, HttpProducer will ignore the Exchange.HTTP_URI header, and use the endpoint's URI for request."
                    + " You may also set the option throwExceptionOnFailure to be false to let the HttpProducer send all the fault response back.")
    boolean bridgeEndpoint;
    @UriParam(label = "producer",
            description = "If the option is true, HttpProducer will set the Host header to the value contained in the current exchange Host header, "
                + "useful in reverse proxy applications where you want the Host header received by the downstream server to reflect the URL called by the upstream client, "
                + "this allows applications which use the Host header to generate accurate URL's for a proxied service")
    boolean preserveHostHeader;
    @UriParam(label = "consumer",
            description = "Whether or not the consumer should try to find a target consumer by matching the URI prefix if no exact match is found.")
    boolean matchOnUriPrefix;
    @UriParam(defaultValue = "true", description = "If this option is false the Servlet will disable the HTTP streaming and set the content-length header on the response")
    boolean chunked = true;
    @UriParam(label = "common",
            description = "Determines whether or not the raw input stream from Servlet is cached or not"
                    + " (Camel will read the stream into a in memory/overflow to file, Stream caching) cache."
                    + " By default Camel will cache the Servlet input stream to support reading it multiple times to ensure it Camel"
                    + " can retrieve all data from the stream. However you can set this option to true when you for example need"
                    + " to access the raw stream, such as streaming it directly to a file or other persistent store."
                    + " DefaultHttpBinding will copy the request input stream into a stream cache and put it into message body"
                    + " if this option is false to support reading the stream multiple times."
                    + " If you use Servlet to bridge/proxy an endpoint then consider enabling this option to improve performance,"
                    + " in case you do not need to read the message payload multiple times."
                    + " The http/http4 producer will by default cache the response body stream. If setting this option to true,"
                    + " then the producers will not cache the response body stream but use the response stream as-is as the message body.")
    boolean disableStreamCache;
    @UriParam(label = "producer", description = "The proxy host name")
    String proxyHost;
    @UriParam(label = "producer", description = "The proxy port number")
    int proxyPort;
    @UriParam(label = "producer", enums = "Basic,Digest,NTLM", description = "Authentication method for proxy, either as Basic, Digest or NTLM.")
    String authMethodPriority;
    @UriParam(description = "If enabled and an Exchange failed processing on the consumer side, and if the caused Exception was send back serialized"
            + " in the response as a application/x-java-serialized-object content type."
            + " On the producer side the exception will be deserialized and thrown as is, instead of the HttpOperationFailedException."
            + " The caused exception is required to be serialized."
            + " This is by default turned off. If you enable this then be aware that Java will deserialize the incoming"
            + " data from the request to Java and that can be a potential security risk.")
    boolean transferException;
    @UriParam(label = "producer", defaultValue = "false", description = "Specifies whether a Connection Close header must be added to HTTP Request. By default connectionClose is false.")
    boolean connectionClose;
    @UriParam(label = "consumer",
            description = "Specifies whether to enable HTTP TRACE for this Servlet consumer. By default TRACE is turned off.")
    boolean traceEnabled;
    @UriParam(label = "consumer",
            description = "Specifies whether to enable HTTP OPTIONS for this Servlet consumer. By default OPTIONS is turned off.")
    boolean optionsEnabled;
    @UriParam(label = "consumer",
            description = "Used to only allow consuming if the HttpMethod matches, such as GET/POST/PUT etc. Multiple methods can be specified separated by comma.")
    String httpMethodRestrict;
    @UriParam(label = "consumer",
            description = "To use a custom buffer size on the javax.servlet.ServletResponse.")
    Integer responseBufferSize;
    @UriParam(label = "producer",
            description = "If this option is true, The http producer won't read response body and cache the input stream")
    boolean ignoreResponseBody;
    @UriParam(label = "producer", defaultValue = "true",
            description = "If this option is true then IN exchange headers will be copied to OUT exchange headers according to copy strategy."
                    + " Setting this to false, allows to only include the headers from the HTTP response (not propagating IN headers).")
    boolean copyHeaders = true;
    @UriParam(label = "consumer",
            description = "Whether to eager check whether the HTTP requests has content if the content-length header is 0 or not present."
                    + " This can be turned on in case HTTP clients do not send streamed data.")
    boolean eagerCheckContentAvailable;
    @UriParam(label = "advanced", defaultValue = "true",
            description = "If this option is true then IN exchange Body of the exchange will be mapped to HTTP body." 
            + " Setting this to false will avoid the HTTP mapping.")
    boolean mapHttpMessageBody = true;
    @UriParam(label = "advanced", defaultValue = "true",
            description = "If this option is true then IN exchange Headers of the exchange will be mapped to HTTP headers."
            + " Setting this to false will avoid the HTTP Headers mapping.")
    boolean mapHttpMessageHeaders = true;
    @UriParam(label = "advanced", defaultValue = "true",
            description = "If this option is true then IN exchange Form Encoded body of the exchange will be mapped to HTTP."
            + " Setting this to false will avoid the HTTP Form Encoded body mapping.")
    boolean mapHttpMessageFormUrlEncodedBody = true;
    @UriParam(label = "producer", defaultValue = "200-299",
            description = "The status codes which is considered a success response. The values are inclusive. The range must be defined as from-to with the dash included.")
    private String okStatusCodeRange = "200-299";
    @UriParam(label = "producer,advanced",
            description = "Refers to a custom org.apache.camel.component.http.UrlRewrite which allows you to rewrite urls when you bridge/proxy endpoints."
                    + " See more details at http://camel.apache.org/urlrewrite.html")
    private UrlRewrite urlRewrite;
    @UriParam(label = "consumer", defaultValue = "false",
            description = "Configure the consumer to work in async mode")
    private boolean async;
    @UriParam(label = "producer", description = "Configure a cookie handler to maintain a HTTP session")
    private CookieHandler cookieHandler;

    public HttpCommonEndpoint() {
    }

    public HttpCommonEndpoint(String endPointURI, HttpCommonComponent component, URI httpURI) throws URISyntaxException {
        super(endPointURI, component);
        this.component = component;
        this.httpUri = httpURI;
    }

    public void connect(HttpConsumer consumer) throws Exception {
        component.connect(consumer);
    }

    public void disconnect(HttpConsumer consumer) throws Exception {
        component.disconnect(consumer);
    }

    @Override
    public HttpCommonComponent getComponent() {
        return (HttpCommonComponent) super.getComponent();
    }

    public boolean isLenientProperties() {
        // true to allow dynamic URI options to be configured and passed to external system for eg. the HttpProducer
        return true;
    }

    public boolean isSingleton() {
        return true;
    }


    // Properties
    //-------------------------------------------------------------------------

    /**
     * @deprecated use {@link #getHttpBinding()}
     */
    @Deprecated
    public HttpBinding getBinding() {
        return httpBinding;
    }

    /**
     * @deprecated use {@link #setHttpBinding(HttpBinding)}
     */
    @Deprecated
    public void setBinding(HttpBinding httpBinding) {
        setHttpBinding(httpBinding);
    }

    public HttpBinding getHttpBinding() {
        if (httpBinding == null) {
            // create a new binding and use the options from this endpoint
            httpBinding = new DefaultHttpBinding();
            httpBinding.setHeaderFilterStrategy(getHeaderFilterStrategy());
            httpBinding.setTransferException(isTransferException());
            if (getComponent() != null) {
                httpBinding.setAllowJavaSerializedObject(getComponent().isAllowJavaSerializedObject());
            }
            httpBinding.setEagerCheckContentAvailable(isEagerCheckContentAvailable());
            httpBinding.setMapHttpMessageBody(isMapHttpMessageBody());
            httpBinding.setMapHttpMessageHeaders(isMapHttpMessageHeaders());
            httpBinding.setMapHttpMessageFormUrlEncodedBody(isMapHttpMessageFormUrlEncodedBody());
        }
        return httpBinding;
    }

    /**
     * To use a custom HttpBinding to control the mapping between Camel message and HttpClient.
     */
    public void setHttpBinding(HttpBinding httpBinding) {
        this.httpBinding = httpBinding;
    }

    public String getPath() {
        //if the path is empty, we just return the default path here
        return httpUri.getPath().length() == 0 ? "/" : httpUri.getPath();
    }

    public int getPort() {
        if (httpUri.getPort() == -1) {
            if ("https".equals(getProtocol())) {
                return 443;
            } else {
                return 80;
            }
        }
        return httpUri.getPort();
    }

    public String getProtocol() {
        return httpUri.getScheme();
    }

    public URI getHttpUri() {
        return httpUri;
    }

    /**
     * The url of the HTTP endpoint to call.
     */
    public void setHttpUri(URI httpUri) {
        this.httpUri = httpUri;
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

    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    /**
     * Option to disable throwing the HttpOperationFailedException in case of failed responses from the remote server.
     * This allows you to get all responses regardless of the HTTP status code.
     */
    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public boolean isBridgeEndpoint() {
        return bridgeEndpoint;
    }

    /**
     * If the option is true, HttpProducer will ignore the Exchange.HTTP_URI header, and use the endpoint's URI for request.
     * You may also set the option throwExceptionOnFailure to be false to let the HttpProducer send all the fault response back.
     */
    public void setBridgeEndpoint(boolean bridge) {
        this.bridgeEndpoint = bridge;
    }

    public boolean isPreserveHostHeader() {
        return preserveHostHeader;
    }

    /**
     * If the option is true, HttpProducer will set the Host header to the value contained in the current exchange Host header,
     * useful in reverse proxy applications where you want the Host header received by the downstream server to reflect the URL called by the upstream client,
     * this allows applications which use the Host header to generate accurate URL's for a proxied service
     */
    public void setPreserveHostHeader(boolean preserveHostHeader) {
        this.preserveHostHeader = preserveHostHeader;
    }

    public boolean isMatchOnUriPrefix() {
        return matchOnUriPrefix;
    }

    /**
     * Whether or not the consumer should try to find a target consumer by matching the URI prefix if no exact match is found.
     * <p/>
     * See more details at: http://camel.apache.org/how-do-i-let-jetty-match-wildcards.html
     */
    public void setMatchOnUriPrefix(boolean match) {
        this.matchOnUriPrefix = match;
    }

    public boolean isDisableStreamCache() {
        return this.disableStreamCache;
    }

    /**
     * Determines whether or not the raw input stream from Servlet is cached or not
     * (Camel will read the stream into a in memory/overflow to file, Stream caching) cache.
     * By default Camel will cache the Servlet input stream to support reading it multiple times to ensure it Camel
     * can retrieve all data from the stream. However you can set this option to true when you for example need
     * to access the raw stream, such as streaming it directly to a file or other persistent store.
     * DefaultHttpBinding will copy the request input stream into a stream cache and put it into message body
     * if this option is false to support reading the stream multiple times.
     * If you use Servlet to bridge/proxy an endpoint then consider enabling this option to improve performance,
     * in case you do not need to read the message payload multiple times.
     + The http/http4 producer will by default cache the response body stream. If setting this option to true,
     + then the producers will not cache the response body stream but use the response stream as-is as the message body.
     */
    public void setDisableStreamCache(boolean disable) {
        this.disableStreamCache = disable;
    }

    public boolean isChunked() {
        return this.chunked;
    }

    /**
     * If this option is false Servlet will disable the HTTP streaming and set the content-length header on the response
     */
    public void setChunked(boolean chunked) {
        this.chunked = chunked;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * The proxy host name
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    /**
     * The proxy port number
     */
    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getAuthMethodPriority() {
        return authMethodPriority;
    }

    /**
     * Authentication method for proxy, either as Basic, Digest or NTLM.
     */
    public void setAuthMethodPriority(String authMethodPriority) {
        this.authMethodPriority = authMethodPriority;
    }

    public boolean isTransferException() {
        return transferException;
    }
    
    public boolean isConnectionClose() {
        return connectionClose;
    }

    /**
     * If this option is true, the producer will add a Connection Close header to HTTP Request
     */
    public void setConnectionClose(boolean connectionClose) {
        this.connectionClose = connectionClose;
    }

    /**
     * If enabled and an Exchange failed processing on the consumer side, and if the caused Exception was send back serialized
     * in the response as a application/x-java-serialized-object content type.
     * On the producer side the exception will be deserialized and thrown as is, instead of the HttpOperationFailedException.
     * The caused exception is required to be serialized.
     * <p/>
     * This is by default turned off. If you enable this then be aware that Java will deserialize the incoming
     * data from the request to Java and that can be a potential security risk.
     */
    public void setTransferException(boolean transferException) {
        this.transferException = transferException;
    }

    public boolean isTraceEnabled() {
        return this.traceEnabled;
    }

    /**
     * Specifies whether to enable HTTP TRACE for this Servlet consumer. By default TRACE is turned off.
     */
    public void setTraceEnabled(boolean traceEnabled) {
        this.traceEnabled = traceEnabled;
    }

    public boolean isOptionsEnabled() {
        return optionsEnabled;
    }

    /**
     * Specifies whether to enable HTTP OPTIONS for this Servlet consumer. By default OPTIONS is turned off.
     */
    public void setOptionsEnabled(boolean optionsEnabled) {
        this.optionsEnabled = optionsEnabled;
    }

    public String getHttpMethodRestrict() {
        return httpMethodRestrict;
    }

    /**
     * Used to only allow consuming if the HttpMethod matches, such as GET/POST/PUT etc.
     * Multiple methods can be specified separated by comma.
     */
    public void setHttpMethodRestrict(String httpMethodRestrict) {
        this.httpMethodRestrict = httpMethodRestrict;
    }

    public UrlRewrite getUrlRewrite() {
        return urlRewrite;
    }

    /**
     * Refers to a custom org.apache.camel.component.http.UrlRewrite which allows you to rewrite urls when you bridge/proxy endpoints.
     * See more details at http://camel.apache.org/urlrewrite.html
     */
    public void setUrlRewrite(UrlRewrite urlRewrite) {
        this.urlRewrite = urlRewrite;
    }

    public Integer getResponseBufferSize() {
        return responseBufferSize;
    }

    /**
     * To use a custom buffer size on the javax.servlet.ServletResponse.
     */
    public void setResponseBufferSize(Integer responseBufferSize) {
        this.responseBufferSize = responseBufferSize;
    }

    public boolean isIgnoreResponseBody() {
        return ignoreResponseBody;
    }

    /**
     * If this option is true, The http producer won't read response body and cache the input stream.
     */
    public void setIgnoreResponseBody(boolean ignoreResponseBody) {
        this.ignoreResponseBody = ignoreResponseBody;
    }

    /**
     * If this option is true then IN exchange headers will be copied to OUT exchange headers according to copy strategy.
     * Setting this to false, allows to only include the headers from the HTTP response (not propagating IN headers).
     */
    public boolean isCopyHeaders() {
        return copyHeaders;
    }

    public void setCopyHeaders(boolean copyHeaders) {
        this.copyHeaders = copyHeaders;
    }

    public boolean isEagerCheckContentAvailable() {
        return eagerCheckContentAvailable;
    }

    /**
     * Whether to eager check whether the HTTP requests has content if the content-length header is 0 or not present.
     * This can be turned on in case HTTP clients do not send streamed data.
     */
    public void setEagerCheckContentAvailable(boolean eagerCheckContentAvailable) {
        this.eagerCheckContentAvailable = eagerCheckContentAvailable;
    }

    public String getOkStatusCodeRange() {
        return okStatusCodeRange;
    }

    /**
     * The status codes which is considered a success response. The values are inclusive. The range must be defined as from-to with the dash included.
     * <p/>
     * The default range is <tt>200-299</tt>
     */
    public void setOkStatusCodeRange(String okStatusCodeRange) {
        this.okStatusCodeRange = okStatusCodeRange;
    }

    public boolean isMapHttpMessageBody() {
        return mapHttpMessageBody;
    }

    /**
     * If this option is true, the IN exchange body will be mapped to HTTP
     */
    public void setMapHttpMessageBody(boolean mapHttpMessageBody) {
        this.mapHttpMessageBody = mapHttpMessageBody;
    }

    public boolean isMapHttpMessageHeaders() {
        return mapHttpMessageHeaders;
    }

    /**
     * If this option is true, the IN exchange headers will be mapped to HTTP Headers
     */
    public void setMapHttpMessageHeaders(boolean mapHttpMessageHeaders) {
        this.mapHttpMessageHeaders = mapHttpMessageHeaders;
    }

    public boolean isMapHttpMessageFormUrlEncodedBody() {
        return mapHttpMessageFormUrlEncodedBody;
    }

    /**
     * If this option is true then IN exchange Form Encoded body will be mapped to HTTP
     */
    public void setMapHttpMessageFormUrlEncodedBody(boolean mapHttpMessageFormUrlEncodedBody) {
        this.mapHttpMessageFormUrlEncodedBody = mapHttpMessageFormUrlEncodedBody;
    }

    public boolean isAsync() {
        return async;
    }

    /**
     * If this option is true, the consumer will work in async mode
     * @param async
     */
    public void setAsync(boolean async) {
        this.async = async;
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
}
