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
package org.apache.camel.component.http4;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.http4.helper.GZIPHelper;
import org.apache.camel.component.http4.helper.HttpProducerHelper;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HTTP;

/**
 * @version $Revision$
 */
public class HttpProducer extends DefaultProducer {
    private static final transient Log LOG = LogFactory.getLog(HttpProducer.class);
    private HttpClient httpClient;
    private boolean throwException;

    public HttpProducer(HttpEndpoint endpoint) {
        super(endpoint);
        this.httpClient = endpoint.getHttpClient();
        this.throwException = endpoint.isThrowExceptionOnFailure();
    }

    public void process(Exchange exchange) throws Exception {
        if (((HttpEndpoint)getEndpoint()).isBridgeEndpoint()) {
            exchange.setProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.TRUE);
        }
        HttpRequestBase httpRequest = createMethod(exchange);
        Message in = exchange.getIn();
        HeaderFilterStrategy strategy = getEndpoint().getHeaderFilterStrategy();

        // propagate headers as HTTP headers
        for (Map.Entry<String, Object> entry : in.getHeaders().entrySet()) {
            String headerValue = in.getHeader(entry.getKey(), String.class);
            if (strategy != null && !strategy.applyFilterToCamelHeaders(entry.getKey(), headerValue, exchange)) {
                httpRequest.addHeader(entry.getKey(), headerValue);
            }
        }
        
        // lets store the result in the output message.
        HttpResponse httpResponse = null;
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Executing http " + httpRequest.getMethod() + " method: " + httpRequest.getURI().toString());
            }
            httpResponse = executeMethod(httpRequest);
            int responseCode = httpResponse.getStatusLine().getStatusCode();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Http responseCode: " + responseCode);
            }

            if (throwException && (responseCode < 100 || responseCode >= 300)) {
                throw populateHttpOperationFailedException(exchange, httpRequest, httpResponse, responseCode);
            } else {
                populateResponse(exchange, httpRequest, httpResponse, in, strategy, responseCode);
            }
        } finally {
            if (httpResponse != null && httpResponse.getEntity() != null) {
                try {
                    httpResponse.getEntity().consumeContent();
                } catch (IOException e) {
                    // nothing we could do
                }
            }
        }
    }

    @Override
    public HttpEndpoint getEndpoint() {
        return (HttpEndpoint) super.getEndpoint();
    }

    protected void populateResponse(Exchange exchange, HttpRequestBase httpRequest, HttpResponse httpResponse, Message in, HeaderFilterStrategy strategy, int responseCode) throws IOException {
        Message answer = exchange.getOut();

        answer.setHeaders(in.getHeaders());
        answer.setHeader(Exchange.HTTP_RESPONSE_CODE, responseCode);
        answer.setBody(extractResponseBody(httpRequest, httpResponse, exchange));

        // propagate HTTP response headers
        Header[] headers = httpResponse.getAllHeaders();
        for (Header header : headers) {
            String name = header.getName();
            String value = header.getValue();
            if (name.toLowerCase().equals("content-type")) {
                name = Exchange.CONTENT_TYPE;
            }
            if (strategy != null && !strategy.applyFilterToExternalHeaders(name, value, exchange)) {
                answer.setHeader(name, value);
            }
        }
    }

    protected HttpOperationFailedException populateHttpOperationFailedException(Exchange exchange, HttpRequestBase httpRequest, HttpResponse httpResponse, int responseCode) throws IOException {
        HttpOperationFailedException exception;
        String uri = httpRequest.getURI().toString();
        String statusText = httpResponse.getStatusLine() != null ? httpResponse.getStatusLine().getReasonPhrase() : null;
        Map<String, String> headers = extractResponseHeaders(httpResponse.getAllHeaders());
        InputStream is = extractResponseBody(httpRequest, httpResponse, exchange);
        // make a defensive copy of the response body in the exception so its detached from the cache
        String copy = null;
        if (is != null) {
            copy = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, is);
        }

        Header locationHeader = httpResponse.getFirstHeader("location");
        if (locationHeader != null && (responseCode >= 300 && responseCode < 400)) {
            exception = new HttpOperationFailedException(uri, responseCode, statusText, locationHeader.getValue(), headers, copy);
        } else {
            exception = new HttpOperationFailedException(uri, responseCode, statusText, null, headers, copy);
        }

        return exception;
    }

    /**
     * Strategy when executing the method (calling the remote server).
     *
     * @param httpRequest the http Request to execute
     * @return the response
     * @throws IOException can be thrown
     */
    protected HttpResponse executeMethod(HttpUriRequest httpRequest) throws IOException {
        return httpClient.execute(httpRequest);
    }

    /**
     * Extracts the response headers
     *
     * @param responseHeaders the headers
     * @return the extracted headers or <tt>null</tt> if no headers existed
     */
    protected static Map<String, String> extractResponseHeaders(Header[] responseHeaders) {
        if (responseHeaders == null || responseHeaders.length == 0) {
            return null;
        }

        Map<String, String> answer = new HashMap<String, String>();
        for (Header header : responseHeaders) {
            answer.put(header.getName(), header.getValue());
        }

        return answer;
    }

    /**
     * Extracts the response from the method as a InputStream.
     *
     * @param httpRequest the method that was executed
     * @return the response as a stream
     * @throws IOException can be thrown
     */
    protected static InputStream extractResponseBody(HttpRequestBase httpRequest, HttpResponse httpResponse, Exchange exchange) throws IOException {
        HttpEntity entity = httpResponse.getEntity();
        if (entity == null) {
            return null;
        }

        InputStream is = entity.getContent();
        if (is == null) {
            return null;
        }

        Header header = httpRequest.getFirstHeader(Exchange.CONTENT_ENCODING);
        String contentEncoding = header != null ? header.getValue() : null;

        if (!exchange.getProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.FALSE, Boolean.class)) {
            is = GZIPHelper.uncompressGzip(contentEncoding, is);
        }
        // Honor the character encoding
        header = httpRequest.getFirstHeader("content-type");
        if (header != null) {
            String contentType = header.getValue();
            // find the charset and set it to the Exchange
            int index = contentType.indexOf("charset=");
            if (index > 0) {
                String charset = contentType.substring(index + 8);
                exchange.setProperty(Exchange.CHARSET_NAME, charset);
            }
        }
        return doExtractResponseBody(is, exchange);
    }

    private static InputStream doExtractResponseBody(InputStream is, Exchange exchange) throws IOException {
        // As httpclient is using a AutoCloseInputStream, it will be closed when the connection is closed
        // we need to cache the stream for it.
        try {
            CachedOutputStream cos = new CachedOutputStream(exchange);
            IOHelper.copy(is, cos);
            return cos.getInputStream();
        } finally {
            ObjectHelper.close(is, "Extracting response body", LOG);
        }
    }

    /**
     * Creates the HttpMethod to use to call the remote server, either its GET or POST.
     *
     * @param exchange the exchange
     * @return the created method as either GET or POST
     * @throws URISyntaxException is thrown if the URI is invalid
     */
    protected HttpRequestBase createMethod(Exchange exchange) throws URISyntaxException {
        String url = HttpProducerHelper.createURL(exchange, getEndpoint());
        URI uri = new URI(url);

        HttpEntity requestEntity = createRequestEntity(exchange);
        HttpMethods methodToUse = HttpProducerHelper.createMethod(exchange, getEndpoint(), requestEntity != null);

        // is a query string provided in the endpoint URI or in a header (header overrules endpoint)
        String queryString = exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
        if (queryString == null) {
            queryString = getEndpoint().getHttpUri().getQuery();
        }

        StringBuilder builder = new StringBuilder(uri.getScheme()).append("://").append(uri.getHost());

        if (uri.getPort() != -1) {
            builder.append(":").append(uri.getPort());
        }

        if (uri.getPath() != null) {
            builder.append(uri.getPath());
        }

        if (queryString != null) {
            builder.append('?');
            builder.append(queryString);
        }

        HttpRequestBase httpRequest = methodToUse.createMethod(builder.toString());

        if (methodToUse.isEntityEnclosing()) {
            ((HttpEntityEnclosingRequestBase) httpRequest).setEntity(requestEntity);
            if (requestEntity != null && requestEntity.getContentType() == null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("No Content-Type provided for URL: " + url + " with exchange: " + exchange);
                }
            }
        }

        return httpRequest;
    }

    /**
     * Creates a holder object for the data to send to the remote server.
     *
     * @param exchange the exchange with the IN message with data to send
     * @return the data holder
     */
    protected HttpEntity createRequestEntity(Exchange exchange) {
        Message in = exchange.getIn();
        if (in.getBody() == null) {
            return null;
        }

        HttpEntity answer = in.getBody(HttpEntity.class);
        if (answer == null) {
            try {
                String data = in.getBody(String.class);
                if (data != null) {
                    String contentType = ExchangeHelper.getContentType(exchange);
                    String charset = exchange.getProperty(Exchange.CHARSET_NAME, String.class);
                    if (charset == null) {
                        charset = HTTP.DEFAULT_CONTENT_CHARSET;
                    }

                    answer = new StringEntity(data, charset);
                    ((StringEntity) answer).setContentEncoding(charset);
                    if (contentType != null) {
                        ((StringEntity) answer).setContentType(contentType);
                    }
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeCamelException(e);
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