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
package org.apache.camel.component.http;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.http.helper.HttpMethodHelper;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.http.base.cookie.CookieHandler;
import org.apache.camel.http.common.HttpHelper;
import org.apache.camel.http.common.HttpProtocolHeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.GZIPHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(HttpProducer.class);

    private static final Integer OK_RESPONSE_CODE = 200;

    private HttpClient httpClient;
    private final HttpContext httpContext;
    private final boolean throwException;
    private final boolean transferException;
    private final HeaderFilterStrategy httpProtocolHeaderFilterStrategy = new HttpProtocolHeaderFilterStrategy();
    private int minOkRange;
    private int maxOkRange;
    private String defaultUrl;
    private URI defaultUri;
    private HttpHost defaultHttpHost;

    public HttpProducer(HttpEndpoint endpoint) {
        super(endpoint);
        this.httpClient = endpoint.getHttpClient();
        this.httpContext = endpoint.getHttpContext();
        this.throwException = endpoint.isThrowExceptionOnFailure();
        this.transferException = endpoint.isTransferException();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        String range = getEndpoint().getOkStatusCodeRange();
        if (!range.contains(",")) {
            // default is 200-299 so lets optimize for this
            if (range.contains("-")) {
                minOkRange = Integer.parseInt(StringHelper.before(range, "-"));
                maxOkRange = Integer.parseInt(StringHelper.after(range, "-"));
            } else {
                minOkRange = Integer.parseInt(range);
                maxOkRange = minOkRange;
            }
        }

        // optimize and build default url when there are no override headers
        String url = getEndpoint().getHttpUri().toASCIIString();
        url = UnsafeUriCharactersEncoder.encodeHttpURI(url);
        URI uri = new URI(url);
        String queryString = getEndpoint().getHttpUri().getRawQuery();
        if (queryString == null) {
            queryString = uri.getRawQuery();
        }
        if (queryString != null) {
            queryString = UnsafeUriCharactersEncoder.encodeHttpURI(queryString);
            uri = URISupport.createURIWithQuery(uri, queryString);
        }
        defaultUri = uri;
        defaultUrl = uri.toASCIIString();
        defaultHttpHost = URIUtils.extractHost(uri);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        boolean cookies = !getEndpoint().getComponent().isCookieManagementDisabled();
        if (cookies && getEndpoint().isClearExpiredCookies() && !getEndpoint().isBridgeEndpoint()) {
            // create the cookies before the invocation
            getEndpoint().getCookieStore().clearExpired(new Date());
        }

        // if we bridge endpoint then we need to skip matching headers with the HTTP_QUERY to avoid sending
        // duplicated headers to the receiver, so use this skipRequestHeaders as the list of headers to skip
        Map<String, Object> skipRequestHeaders = null;

        if (getEndpoint().isBridgeEndpoint()) {
            exchange.setProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.TRUE);
            String queryString = exchange.getIn().getHeader(HttpConstants.HTTP_QUERY, String.class);
            if (queryString != null) {
                skipRequestHeaders = URISupport.parseQuery(queryString, false, true);
            }
        }

        HttpUriRequest httpRequest = createMethod(exchange);
        HttpHost httpHost = createHost(httpRequest);

        Message in = exchange.getIn();
        String httpProtocolVersion = in.getHeader(HttpConstants.HTTP_PROTOCOL_VERSION, String.class);
        if (httpProtocolVersion != null) {
            // set the HTTP protocol version
            int[] version = HttpHelper.parserHttpVersion(httpProtocolVersion);
            httpRequest.setVersion(new HttpVersion(version[0], version[1]));
        }

        HeaderFilterStrategy strategy = getEndpoint().getHeaderFilterStrategy();

        if (!getEndpoint().isSkipRequestHeaders()) {
            // propagate headers as HTTP headers
            if (strategy != null) {
                final TypeConverter tc = exchange.getContext().getTypeConverter();
                for (Map.Entry<String, Object> entry : in.getHeaders().entrySet()) {
                    String key = entry.getKey();
                    // we should not add headers for the parameters in the uri if we bridge the endpoint
                    // as then we would duplicate headers on both the endpoint uri, and in HTTP headers as well
                    if (skipRequestHeaders != null && skipRequestHeaders.containsKey(key)) {
                        continue;
                    }
                    Object headerValue = entry.getValue();

                    if (headerValue != null) {
                        if (headerValue instanceof String || headerValue instanceof Integer || headerValue instanceof Long
                                || headerValue instanceof Boolean || headerValue instanceof Date) {
                            // optimise for common types
                            String value = headerValue.toString();
                            if (!strategy.applyFilterToCamelHeaders(key, value, exchange)) {
                                httpRequest.addHeader(key, value);
                            }
                            continue;
                        }

                        // use an iterator as there can be multiple values. (must not use a delimiter, and allow empty values)
                        final Iterator<?> it = ObjectHelper.createIterator(headerValue, null, true);

                        // the value to add as request header
                        List<String> multiValues = null;
                        String prev = null;

                        // if its a multi value then check each value if we can add it and for multi values they
                        // should be combined into a single value
                        while (it.hasNext()) {
                            String value = tc.convertTo(String.class, it.next());
                            if (value != null && !strategy.applyFilterToCamelHeaders(key, value, exchange)) {
                                if (prev == null) {
                                    prev = value;
                                } else {
                                    // only create array for multi values when really needed
                                    if (multiValues == null) {
                                        multiValues = new ArrayList<>();
                                        multiValues.add(prev);
                                    }
                                    multiValues.add(value);
                                }
                            }
                        }

                        // add the value(s) as a http request header
                        if (multiValues != null) {
                            // use the default toString of a ArrayList to create in the form [xxx, yyy]
                            // if multi valued, for a single value, then just output the value as is
                            String s = multiValues.size() > 1 ? multiValues.toString() : multiValues.get(0);
                            httpRequest.addHeader(key, s);
                        } else if (prev != null) {
                            httpRequest.addHeader(key, prev);
                        }
                    }
                }
            }
        }

        if (getEndpoint().getCookieHandler() != null) {
            Map<String, List<String>> cookieHeaders
                    = getEndpoint().getCookieHandler().loadCookies(exchange, httpRequest.getUri());
            for (Map.Entry<String, List<String>> entry : cookieHeaders.entrySet()) {
                String key = entry.getKey();
                if (!entry.getValue().isEmpty()) {
                    // join multi-values separated by semi-colon
                    httpRequest.addHeader(key, String.join(";", entry.getValue()));
                }
            }
        }

        if (getEndpoint().getCustomHostHeader() != null) {
            httpRequest.setHeader(HttpConstants.HTTP_HEADER_HOST, getEndpoint().getCustomHostHeader());
        }
        //In reverse proxy applications it can be desirable for the downstream service to see the original Host header
        //if this option is set, and the exchange Host header is not null, we will set it's current value on the httpRequest
        if (getEndpoint().isPreserveHostHeader()) {
            String hostHeader = exchange.getIn().getHeader(HttpConstants.HTTP_HEADER_HOST, String.class);
            if (hostHeader != null) {
                //HttpClient 4 will check to see if the Host header is present, and use it if it is, see org.apache.http.protocol.RequestTargetHost in httpcore
                httpRequest.setHeader(HttpConstants.HTTP_HEADER_HOST, hostHeader);
            }
        }

        if (getEndpoint().isConnectionClose()) {
            httpRequest.addHeader("Connection", HeaderElements.CLOSE);
        }

        // lets store the result in the output message.
        try {
            executeMethod(
                    httpHost, httpRequest,
                    httpResponse -> {
                        try {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Executing http {} method: {}", httpRequest.getMethod(), httpRequest.getUri());
                            }
                            int responseCode = httpResponse.getCode();
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Http responseCode: {}", responseCode);
                            }

                            if (!throwException) {
                                // if we do not use failed exception then populate response for all response codes
                                HttpProducer.this.populateResponse(exchange, httpRequest, httpResponse, strategy, responseCode);
                            } else {
                                boolean ok;
                                if (minOkRange > 0) {
                                    ok = responseCode >= minOkRange && responseCode <= maxOkRange;
                                } else {
                                    ok = HttpHelper.isStatusCodeOk(responseCode,
                                            HttpProducer.this.getEndpoint().getOkStatusCodeRange());
                                }
                                if (ok) {
                                    // only populate response for OK response
                                    HttpProducer.this.populateResponse(exchange, httpRequest, httpResponse, strategy,
                                            responseCode);
                                } else {
                                    // operation failed so populate exception to throw
                                    throw HttpProducer.this.populateHttpOperationFailedException(exchange, httpRequest,
                                            httpResponse, responseCode);
                                }
                            }
                        } catch (IOException | HttpException | RuntimeCamelException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new RuntimeCamelException(e);
                        } finally {
                            if (httpResponse != null && HttpProducer.this.getEndpoint().isDisableStreamCache()) {
                                // close the stream at the end of the exchange to ensure it gets eventually closed later
                                exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                                    @Override
                                    public void onDone(Exchange exchange1) {
                                        try {
                                            EntityUtils.consume(httpResponse.getEntity());
                                        } catch (Exception e) {
                                            // ignore
                                        } finally {
                                            try {
                                                EntityUtils.consume(httpRequest.getEntity());
                                            } catch (Exception e) {
                                                // ignore
                                            }
                                        }
                                    }
                                });
                            } else if (httpResponse != null) {
                                // close the stream now
                                try {
                                    EntityUtils.consume(httpResponse.getEntity());
                                } catch (Exception e) {
                                    // ignore
                                } finally {
                                    try {
                                        EntityUtils.consume(httpRequest.getEntity());
                                    } catch (Exception e) {
                                        // ignore
                                    }
                                }
                            }
                        }
                        return null;
                    });
        } catch (RuntimeCamelException e) {
            if (e.getCause() instanceof Exception ex) {
                // Rethrow the embedded exception to simulate the same behavior as with version 4
                throw ex;
            }
            throw e;
        }
    }

    @Override
    public HttpEndpoint getEndpoint() {
        return (HttpEndpoint) super.getEndpoint();
    }

    protected void populateResponse(
            Exchange exchange, HttpUriRequest httpRequest, ClassicHttpResponse httpResponse,
            HeaderFilterStrategy strategy, int responseCode)
            throws IOException, ClassNotFoundException {
        // We just make the out message is not create when extractResponseBody throws exception
        Object response = extractResponseBody(httpResponse, exchange, getEndpoint().isIgnoreResponseBody());
        Message answer = exchange.getOut();

        // optimize for 200 response code as the boxing is outside the cached integers
        if (responseCode == 200) {
            answer.setHeader(HttpConstants.HTTP_RESPONSE_CODE, OK_RESPONSE_CODE);
        } else {
            answer.setHeader(HttpConstants.HTTP_RESPONSE_CODE, responseCode);
        }
        if (httpResponse.getReasonPhrase() != null) {
            answer.setHeader(HttpConstants.HTTP_RESPONSE_TEXT, httpResponse.getReasonPhrase());
        }
        answer.setBody(response);

        if (!getEndpoint().isSkipResponseHeaders()) {

            // propagate HTTP response headers
            Map<String, List<String>> cookieHeaders = null;
            CookieHandler cookieHandler = getEndpoint().getCookieHandler();
            if (cookieHandler != null) {
                cookieHeaders = new HashMap<>();
            }

            // optimize to walk headers with an iterator which does not create a new array as getAllHeaders does
            boolean found = false;
            Iterator<Header> it = httpResponse.headerIterator();
            while (it.hasNext()) {
                Header header = it.next();
                String name = header.getName();
                String value = header.getValue();
                if (cookieHeaders != null) {
                    cookieHeaders.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
                }
                if (!found && name.equalsIgnoreCase("content-type")) {
                    name = Exchange.CONTENT_TYPE;
                    exchange.setProperty(ExchangePropertyKey.CHARSET_NAME, IOHelper.getCharsetNameFromContentType(value));
                    found = true;
                }
                // use http helper to extract parameter value as it may contain multiple values
                Object extracted = HttpHelper.extractHttpParameterValue(value);
                if (strategy != null && !strategy.applyFilterToExternalHeaders(name, extracted, exchange)) {
                    HttpHelper.appendHeader(answer.getHeaders(), name, extracted);
                }
            }
            // handle cookies
            if (cookieHandler != null) {
                try {
                    cookieHandler.storeCookies(exchange, httpRequest.getUri(), cookieHeaders);
                } catch (URISyntaxException e) {
                    throw new RuntimeCamelException(e);
                }
            }
        }

        // endpoint might be configured to copy headers from in to out
        // to avoid overriding existing headers with old values just
        // filter the http protocol headers
        if (getEndpoint().isCopyHeaders()) {
            MessageHelper.copyHeaders(exchange.getIn(), answer, httpProtocolHeaderFilterStrategy, false);
        }
    }

    protected Exception populateHttpOperationFailedException(
            Exchange exchange, HttpUriRequest httpRequest, ClassicHttpResponse httpResponse, int responseCode)
            throws IOException, ClassNotFoundException {
        Exception answer;

        String statusText = httpResponse.getReasonPhrase() != null ? httpResponse.getReasonPhrase() : null;
        Map<String, String> headers = extractResponseHeaders(httpResponse.getHeaders());
        // handle cookies
        CookieHandler cookieHandler = getEndpoint().getCookieHandler();
        if (cookieHandler != null) {
            Map<String, List<String>> m = new HashMap<>();
            for (Entry<String, String> e : headers.entrySet()) {
                m.put(e.getKey(), Collections.singletonList(e.getValue()));
            }
            try {
                cookieHandler.storeCookies(exchange, httpRequest.getUri(), m);
            } catch (URISyntaxException e) {
                throw new RuntimeCamelException(e);
            }
        }

        Object responseBody = extractResponseBody(httpResponse, exchange, getEndpoint().isIgnoreResponseBody());
        if (transferException && responseBody instanceof Exception ex) {
            // if the response was a serialized exception then use that
            return ex;
        }

        // make a defensive copy of the response body in the exception so its detached from the cache
        String copy = null;
        if (responseBody != null) {
            copy = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, responseBody);
        }

        Header locationHeader = httpResponse.getFirstHeader("location");
        try {
            String uri = httpRequest.getUri().toString();
            if (locationHeader != null && responseCode >= 300 && responseCode < 400) {
                answer = new HttpOperationFailedException(
                        uri, responseCode, statusText, locationHeader.getValue(), headers, copy);
            } else {
                answer = new HttpOperationFailedException(uri, responseCode, statusText, null, headers, copy);
            }
        } catch (URISyntaxException e) {
            throw new RuntimeCamelException(e);
        }

        return answer;
    }

    /**
     * Strategy when executing the method (calling the remote server).
     *
     * @param  httpHost    the http host to call
     * @param  httpRequest the http request to execute
     * @param  handler     the response handler
     * @return             the response
     * @throws IOException can be thrown
     */
    protected <T> T executeMethod(HttpHost httpHost, HttpUriRequest httpRequest, HttpClientResponseHandler<T> handler)
            throws IOException {
        HttpContext localContext = HttpClientContext.create();
        if (httpContext != null) {
            localContext = new BasicHttpContext(httpContext);
        }
        return httpClient.execute(httpHost, httpRequest, localContext, handler);
    }

    /**
     * Extracts the response headers
     *
     * @param  responseHeaders the headers
     * @return                 the extracted headers or an empty map if no headers existed
     */
    protected static Map<String, String> extractResponseHeaders(Header[] responseHeaders) {
        if (responseHeaders == null || responseHeaders.length == 0) {
            return Map.of();
        }

        Map<String, String> answer = new HashMap<>();
        for (Header header : responseHeaders) {
            answer.put(header.getName(), header.getValue());
        }

        return answer;
    }

    /**
     * Extracts the response from the method as a InputStream.
     */
    protected Object extractResponseBody(
            ClassicHttpResponse httpResponse, Exchange exchange, boolean ignoreResponseBody)
            throws IOException, ClassNotFoundException {
        HttpEntity entity = httpResponse.getEntity();
        if (entity == null) {
            return null;
        }

        InputStream is = entity.getContent();
        if (is == null) {
            return null;
        }

        Header header = httpResponse.getFirstHeader(HttpConstants.CONTENT_ENCODING);
        String contentEncoding = header != null ? header.getValue() : null;

        final boolean gzipEncoding = exchange.getProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.FALSE, Boolean.class);
        if (!gzipEncoding) {
            is = GZIPHelper.uncompressGzip(contentEncoding, is);
        }
        // Honor the character encoding
        String contentType = null;
        header = httpResponse.getFirstHeader("content-type");
        if (header != null) {
            contentType = header.getValue();
            // find the charset and set it to the Exchange
            HttpHelper.setCharsetFromContentType(contentType, exchange);
        }
        // if content type is a serialized java object then de-serialize it back to a Java object
        if (contentType != null && contentType.equals(HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT)) {
            // only deserialize java if allowed
            if (getEndpoint().getComponent().isAllowJavaSerializedObject() || getEndpoint().isTransferException()) {
                return HttpHelper.deserializeJavaObjectFromStream(is, exchange.getContext());
            } else {
                // empty response
                return null;
            }
        } else {
            if (!getEndpoint().isDisableStreamCache()) {
                if (ignoreResponseBody) {
                    // ignore response
                    return null;
                }
                int max = getEndpoint().getComponent().getResponsePayloadStreamingThreshold();
                if (max > 0) {
                    // optimize when we have content-length for small sizes to avoid creating streaming objects
                    long len = entity.getContentLength();
                    if (len > 0 && len <= max) {
                        int i = (int) len;
                        byte[] arr = new byte[i];
                        int read = 0;
                        int offset = 0;
                        int remain = i;
                        while ((read = is.read(arr, offset, remain)) > 0 && remain > 0) {
                            offset += read;
                            remain -= read;
                        }
                        IOHelper.close(is);
                        return arr;
                    }
                }
                // else for bigger payloads then wrap the response in a stream cache so its re-readable
                return doExtractResponseBodyAsStream(is, exchange);
            } else {
                // use the response stream as-is
                return is;
            }
        }
    }

    private InputStream doExtractResponseBodyAsStream(InputStream is, Exchange exchange) throws IOException {
        // As httpclient is using a AutoCloseInputStream, it will be closed when the connection is closed
        // we need to cache the stream for it.
        CachedOutputStream cos = null;
        try {
            // This CachedOutputStream will not be closed when the exchange is onCompletion
            cos = new CachedOutputStream(exchange, false);
            IOHelper.copy(is, cos);
            // When the InputStream is closed, the CachedOutputStream will be closed
            return cos.getWrappedInputStream();
        } catch (IOException ex) {
            // try to close the CachedOutputStream when we get the IOException
            try {
                cos.close();
            } catch (IOException ignore) {
                //do nothing here
            }
            throw ex;
        } finally {
            IOHelper.close(is, "Extracting response body", LOG);
        }
    }

    /**
     * Creates the HttpHost to use to call the remote server
     */
    protected HttpHost createHost(HttpUriRequest httpRequest) {
        try {
            URI uri = httpRequest.getUri();
            if (uri.equals(defaultUri)) {
                return defaultHttpHost;
            }
            return HttpHost.create(uri);
        } catch (URISyntaxException e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * Creates the HttpMethod to use to call the remote server, either its GET or POST.
     *
     * @param  exchange           the exchange
     * @return                    the created method as either GET or POST
     * @throws URISyntaxException is thrown if the URI is invalid
     * @throws Exception          is thrown if error creating RequestEntity
     */
    protected HttpUriRequest createMethod(Exchange exchange) throws Exception {
        if (defaultUri == null || defaultUrl == null) {
            throw new IllegalArgumentException("Producer must be started");
        }
        String url = defaultUrl;
        URI uri = defaultUri;

        // the exchange can have some headers that override the default url and forces to create
        // a new url that is dynamic based on header values
        // these checks are checks that is done in HttpHelper.createURL and HttpHelper.createURI methods
        boolean create = false;
        Message in = exchange.getIn();
        if (in.getHeader(HttpConstants.REST_HTTP_URI) != null) {
            create = true;
        } else if (in.getHeader(HttpConstants.HTTP_URI) != null && !getEndpoint().isBridgeEndpoint()) {
            create = true;
        } else if (in.getHeader(HttpConstants.HTTP_PATH) != null) {
            create = true;
        } else if (in.getHeader(HttpConstants.REST_HTTP_QUERY) != null) {
            create = true;
        } else if (in.getHeader(HttpConstants.HTTP_RAW_QUERY) != null) {
            create = true;
        } else if (in.getHeader(HttpConstants.HTTP_QUERY) != null) {
            create = true;
        }

        if (create) {
            // creating the url to use takes 2-steps
            url = HttpHelper.createURL(exchange, getEndpoint());
            uri = HttpHelper.createURI(exchange, url, getEndpoint());
            // get the url from the uri
            url = uri.toASCIIString();
        }

        // create http holder objects for the request
        HttpMethods methodToUse = HttpMethodHelper.createMethod(exchange, getEndpoint());
        HttpUriRequest method = methodToUse.createMethod(uri);

        // special for HTTP DELETE/GET if the message body should be included
        if (getEndpoint().isDeleteWithBody() && "DELETE".equals(method.getMethod())
                || getEndpoint().isGetWithBody() && "GET".equals(method.getMethod())) {
            method.setEntity(createRequestEntity(exchange));
        }

        LOG.trace("Using URL: {} with method: {}", url, method);

        if (methodToUse.isEntityEnclosing()) {
            // only create entity for http payload if the HTTP method carries payload (such as POST)
            HttpEntity requestEntity = createRequestEntity(exchange);
            method.setEntity(requestEntity);
            if (requestEntity != null && requestEntity.getContentType() == null) {
                LOG.debug("No Content-Type provided for URL: {} with exchange: {}", url, exchange);
            }
        }

        // there must be a host on the method
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException(
                    "Invalid url: " + url + ". If you are forwarding/bridging http endpoints, then enable the bridgeEndpoint option on the endpoint: "
                                               + getEndpoint());
        }

        return method;
    }

    /**
     * Creates a holder object for the data to send to the remote server.
     *
     * @param  exchange               the exchange with the IN message with data to send
     * @return                        the data holder
     * @throws CamelExchangeException is thrown if error creating RequestEntity
     */
    protected HttpEntity createRequestEntity(Exchange exchange) throws CamelExchangeException {
        HttpEntity answer = null;

        Message in = exchange.getIn();
        Object body = in.getBody();
        try {
            if (body == null) {
                return null;
            } else if (body instanceof HttpEntity entity) {
                answer = entity;
                // special optimized for using these 3 type converters for common message payload types
            } else if (body instanceof byte[] bytes) {
                answer = HttpEntityConverter.toHttpEntity(bytes, exchange);
            } else if (body instanceof InputStream is) {
                answer = HttpEntityConverter.toHttpEntity(is, exchange);
            } else if (body instanceof String content) {
                answer = HttpEntityConverter.toHttpEntity(content, exchange);
            }
        } catch (Exception e) {
            throw new CamelExchangeException("Error creating RequestEntity from message body", exchange, e);
        }

        if (answer == null) {
            try {
                Object data = in.getBody();
                if (data != null) {
                    String contentTypeString = ExchangeHelper.getContentType(exchange);
                    ContentType contentType = null;

                    //Check the contentType is valid or not, If not it throws an exception.
                    //When ContentType.parse parse method parse "multipart/form-data;boundary=---------------------------j2radvtrk",
                    //it removes "boundary" from Content-Type; I have to use contentType.create method.
                    if (contentTypeString != null) {
                        // using ContentType.parser for charset
                        if (contentTypeString.contains("charset") || contentTypeString.contains(";")) {
                            contentType = ContentType.parse(contentTypeString);
                        } else {
                            contentType = ContentType.create(contentTypeString);
                        }
                    }

                    if (contentTypeString != null
                            && HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT.equals(contentTypeString)) {
                        if (!getEndpoint().getComponent().isAllowJavaSerializedObject()) {
                            throw new CamelExchangeException(
                                    "Content-type " + HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT
                                                             + " is not allowed",
                                    exchange);
                        }
                        // serialized java object
                        Serializable obj = in.getMandatoryBody(Serializable.class);
                        // write object to output stream
                        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                            HttpHelper.writeObjectToStream(bos, obj);
                            answer = new ByteArrayEntity(bos.toByteArray(), HttpConstants.JAVA_SERIALIZED_OBJECT);
                        }
                    } else if (data instanceof File || data instanceof GenericFile) {
                        // file based (could potentially also be a FTP file etc)
                        File file = in.getBody(File.class);
                        if (file != null) {
                            answer = new FileEntity(file, contentType);
                        }
                    } else if (data instanceof String content) {
                        // be a bit careful with String as any type can most likely be converted to String
                        // so we only do an instanceof check and accept String if the body is really a String
                        // do not fallback to use the default charset as it can influence the request
                        // (for example application/x-www-form-urlencoded forms being sent)
                        String charset = ExchangeHelper.getCharsetName(exchange, false);
                        if (charset == null && contentType != null) {
                            // okay try to get the charset from the content-type
                            Charset cs = contentType.getCharset();
                            if (cs != null) {
                                charset = cs.name();
                            }
                        }

                        // sync contentType.charset and charset
                        if (contentType != null && contentType.getCharset() == null && charset != null) {
                            contentType = ContentType.parse(contentType + ";charset=" + charset);
                        }

                        answer = new StringEntity(content, contentType, charset, false);
                    }

                    // fallback as input stream
                    if (answer == null) {
                        // force the body as an input stream since this is the fallback
                        InputStream is = in.getMandatoryBody(InputStream.class);

                        answer = new InputStreamEntity(is, -1, contentType);
                    }
                }
            } catch (UnsupportedEncodingException e) {
                throw new CamelExchangeException("Error creating RequestEntity from message body", exchange, e);
            } catch (IOException e) {
                throw new CamelExchangeException("Error serializing message body", exchange, e);
            }
        }
        return answer;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

}
