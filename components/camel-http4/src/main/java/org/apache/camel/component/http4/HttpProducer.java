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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.http4.helper.GZIPHelper;
import org.apache.camel.component.http4.helper.HttpHelper;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public class HttpProducer extends DefaultProducer {
    private static final transient Logger LOG = LoggerFactory.getLogger(HttpProducer.class);
    private HttpClient httpClient;
    private boolean throwException;
    private boolean transferException;

    public HttpProducer(HttpEndpoint endpoint) {
        super(endpoint);
        this.httpClient = endpoint.getHttpClient();
        this.throwException = endpoint.isThrowExceptionOnFailure();
        this.transferException = endpoint.isTransferException();
    }

    public void process(Exchange exchange) throws Exception {
        if (getEndpoint().isBridgeEndpoint()) {
            exchange.setProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.TRUE);
        }
        HttpRequestBase httpRequest = createMethod(exchange);
        Message in = exchange.getIn();
        String httpProtocolVersion = in.getHeader(Exchange.HTTP_PROTOCOL_VERSION, String.class);
        if (httpProtocolVersion != null) {
            // set the HTTP protocol version
            httpRequest.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpHelper.parserHttpVersion(httpProtocolVersion));
        }
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
            if (httpResponse != null) {
                try {
                    EntityUtils.consume(httpResponse.getEntity());
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

    protected void populateResponse(Exchange exchange, HttpRequestBase httpRequest, HttpResponse httpResponse,
                                    Message in, HeaderFilterStrategy strategy, int responseCode) throws IOException, ClassNotFoundException {
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

    protected Exception populateHttpOperationFailedException(Exchange exchange, HttpRequestBase httpRequest, HttpResponse httpResponse, int responseCode) throws IOException, ClassNotFoundException {
        Exception answer;

        String uri = httpRequest.getURI().toString();
        String statusText = httpResponse.getStatusLine() != null ? httpResponse.getStatusLine().getReasonPhrase() : null;
        Map<String, String> headers = extractResponseHeaders(httpResponse.getAllHeaders());

        Object responseBody = extractResponseBody(httpRequest, httpResponse, exchange);
        if (transferException && responseBody != null && responseBody instanceof Exception) {
            // if the response was a serialized exception then use that
            return (Exception) responseBody;
        }

        // make a defensive copy of the response body in the exception so its detached from the cache
        String copy = null;
        if (responseBody != null) {
            copy = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, responseBody);
        }

        Header locationHeader = httpResponse.getFirstHeader("location");
        if (locationHeader != null && (responseCode >= 300 && responseCode < 400)) {
            answer = new HttpOperationFailedException(uri, responseCode, statusText, locationHeader.getValue(), headers, copy);
        } else {
            answer = new HttpOperationFailedException(uri, responseCode, statusText, null, headers, copy);
        }

        return answer;
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
     * @return the response either as a stream, or as a deserialized java object
     * @throws IOException can be thrown
     */
    protected static Object extractResponseBody(HttpRequestBase httpRequest, HttpResponse httpResponse, Exchange exchange) throws IOException, ClassNotFoundException {
        HttpEntity entity = httpResponse.getEntity();
        if (entity == null) {
            return null;
        }

        InputStream is = entity.getContent();
        if (is == null) {
            return null;
        }

        Header header = httpResponse.getFirstHeader(Exchange.CONTENT_ENCODING);
        String contentEncoding = header != null ? header.getValue() : null;

        if (!exchange.getProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.FALSE, Boolean.class)) {
            is = GZIPHelper.uncompressGzip(contentEncoding, is);
        }
        // Honor the character encoding
        String contentType = null;
        header = httpRequest.getFirstHeader("content-type");
        if (header != null) {
            contentType = header.getValue();
            // find the charset and set it to the Exchange
            HttpHelper.setCharsetFromContentType(contentType, exchange);
        }
        InputStream response = doExtractResponseBodyAsStream(is, exchange);
        // if content type is a serialized java object then de-serialize it back to a Java object
        if (contentType != null && contentType.equals(HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT)) {
            return HttpHelper.deserializeJavaObjectFromStream(response);
        } else {
            return response;
        }
    }

    private static InputStream doExtractResponseBodyAsStream(InputStream is, Exchange exchange) throws IOException {
        // As httpclient is using a AutoCloseInputStream, it will be closed when the connection is closed
        // we need to cache the stream for it.
        try {
            // This CachedOutputStream will not be closed when the exchange is onCompletion
            CachedOutputStream cos = new CachedOutputStream(exchange, false);
            IOHelper.copy(is, cos);
            // When the InputStream is closed, the CachedOutputStream will be closed
            return cos.getWrappedInputStream();
        } finally {
            IOHelper.close(is, "Extracting response body", LOG);
        }
    }

    /**
     * Creates the HttpMethod to use to call the remote server, either its GET or POST.
     *
     * @param exchange the exchange
     * @return the created method as either GET or POST
     * @throws URISyntaxException is thrown if the URI is invalid
     * @throws CamelExchangeException is thrown if error creating RequestEntity
     */
    protected HttpRequestBase createMethod(Exchange exchange) throws URISyntaxException, CamelExchangeException {
        String url = HttpHelper.createURL(exchange, getEndpoint());
        URI uri = new URI(url);

        HttpEntity requestEntity = createRequestEntity(exchange);
        HttpMethods methodToUse = HttpHelper.createMethod(exchange, getEndpoint(), requestEntity != null);

        // is a query string provided in the endpoint URI or in a header (header overrules endpoint)
        String queryString = exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
        if (queryString == null) {
            queryString = getEndpoint().getHttpUri().getRawQuery();
        }

        StringBuilder builder = new StringBuilder(uri.getScheme()).append("://").append(uri.getHost());

        if (uri.getPort() != -1) {
            builder.append(":").append(uri.getPort());
        }

        if (uri.getPath() != null) {
            builder.append(uri.getRawPath());
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
     * @throws CamelExchangeException is thrown if error creating RequestEntity
     */
    protected HttpEntity createRequestEntity(Exchange exchange) throws CamelExchangeException {
        Message in = exchange.getIn();
        if (in.getBody() == null) {
            return null;
        }

        HttpEntity answer = in.getBody(HttpEntity.class);
        if (answer == null) {
            try {
                Object data = in.getBody();
                if (data != null) {
                    String contentType = ExchangeHelper.getContentType(exchange);

                    if (contentType != null && HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT.equals(contentType)) {
                        // serialized java object
                        Serializable obj = in.getMandatoryBody(Serializable.class);
                        // write object to output stream
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        HttpHelper.writeObjectToStream(bos, obj);
                        ByteArrayEntity entity = new ByteArrayEntity(bos.toByteArray());
                        entity.setContentType(HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT);
                        IOHelper.close(bos);
                        answer = entity;
                    } else if (data instanceof File || data instanceof GenericFile) {
                        // file based (could potentially also be a FTP file etc)
                        File file = in.getBody(File.class);
                        if (file != null) {
                            answer = new FileEntity(file, contentType);
                        }
                    } else if (data instanceof String) {
                        // be a bit careful with String as any type can most likely be converted to String
                        // so we only do an instanceof check and accept String if the body is really a String
                        // do not fallback to use the default charset as it can influence the request
                        // (for example application/x-www-form-urlencoded forms being sent)
                        String charset = IOConverter.getCharsetName(exchange, false);
                        StringEntity entity = new StringEntity((String) data, charset);
                        entity.setContentType(contentType);
                        answer = entity;
                    }

                    // fallback as input stream
                    if (answer == null) {
                        // force the body as an input stream since this is the fallback
                        InputStream is = in.getMandatoryBody(InputStream.class);
                        InputStreamEntity entity = new InputStreamEntity(is, -1);
                        entity.setContentType(contentType);
                        answer = entity;
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
