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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.http4.helper.HttpHelper;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.GZIPHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public class HttpProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(HttpProducer.class);
    private HttpClient httpClient;
    private HttpContext httpContext;
    private boolean throwException;
    private boolean transferException;

    public HttpProducer(HttpEndpoint endpoint) {
        super(endpoint);
        this.httpClient = endpoint.getHttpClient();
        this.httpContext = endpoint.getHttpContext();
        this.throwException = endpoint.isThrowExceptionOnFailure();
        this.transferException = endpoint.isTransferException();
    }

    public void process(Exchange exchange) throws Exception {

        if (getEndpoint().isClearExpiredCookies()) {
            if (httpClient instanceof DefaultHttpClient) {
                boolean cleared = ((DefaultHttpClient) httpClient).getCookieStore().clearExpired(new Date());
                log.debug("Any expired cookies cleared: {}", cleared);
            }
        }

        // if we bridge endpoint then we need to skip matching headers with the HTTP_QUERY to avoid sending
        // duplicated headers to the receiver, so use this skipRequestHeaders as the list of headers to skip
        Map<String, Object> skipRequestHeaders = null;

        if (getEndpoint().isBridgeEndpoint()) {
            exchange.setProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.TRUE);
            String queryString = exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
            if (queryString != null) {
                skipRequestHeaders = URISupport.parseQuery(queryString);
            }
            // Need to remove the Host key as it should be not used 
            exchange.getIn().getHeaders().remove("host");
        }
        HttpRequestBase httpRequest = createMethod(exchange);
        if (getEndpoint().isAuthenticationPreemptive()) {
            Credentials creds = ((DefaultHttpClient) httpClient).getCredentialsProvider().getCredentials(AuthScope.ANY);
            httpRequest.addHeader(new BasicScheme().authenticate(creds, httpRequest));
        }
        Message in = exchange.getIn();
        String httpProtocolVersion = in.getHeader(Exchange.HTTP_PROTOCOL_VERSION, String.class);
        if (httpProtocolVersion != null) {
            // set the HTTP protocol version
            httpRequest.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpHelper.parserHttpVersion(httpProtocolVersion));
        }
        HeaderFilterStrategy strategy = getEndpoint().getHeaderFilterStrategy();

        // propagate headers as HTTP headers
        for (Map.Entry<String, Object> entry : in.getHeaders().entrySet()) {
            String key = entry.getKey();
            Object headerValue = in.getHeader(key);

            if (headerValue != null) {
                // use an iterator as there can be multiple values. (must not use a delimiter, and allow empty values)
                final Iterator<?> it = ObjectHelper.createIterator(headerValue, null, true);

                // the value to add as request header
                final List<String> values = new ArrayList<String>();

                // if its a multi value then check each value if we can add it and for multi values they
                // should be combined into a single value
                while (it.hasNext()) {
                    String value = exchange.getContext().getTypeConverter().convertTo(String.class, it.next());

                    // we should not add headers for the parameters in the uri if we bridge the endpoint
                    // as then we would duplicate headers on both the endpoint uri, and in HTTP headers as well
                    if (skipRequestHeaders != null && skipRequestHeaders.containsKey(key)) {
                        continue;
                    }
                    if (value != null && strategy != null && !strategy.applyFilterToCamelHeaders(key, value, exchange)) {
                        values.add(value);
                    }
                }

                // add the value(s) as a http request header
                if (values.size() > 0) {
                    // use the default toString of a ArrayList to create in the form [xxx, yyy]
                    // if multi valued, for a single value, then just output the value as is
                    String s =  values.size() > 1 ? values.toString() : values.get(0);
                    httpRequest.addHeader(key, s);
                }
            }
        }

        // lets store the result in the output message.
        HttpResponse httpResponse = null;
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Executing http {} method: {}", httpRequest.getMethod(), httpRequest.getURI().toString());
            }
            httpResponse = executeMethod(httpRequest);
            int responseCode = httpResponse.getStatusLine().getStatusCode();
            LOG.debug("Http responseCode: {}", responseCode);

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
        // We just make the out message is not create when extractResponseBody throws exception
        Object response = extractResponseBody(httpRequest, httpResponse, exchange);
        Message answer = exchange.getOut();

        answer.setHeader(Exchange.HTTP_RESPONSE_CODE, responseCode);
        answer.setBody(response);

        // propagate HTTP response headers
        Header[] headers = httpResponse.getAllHeaders();
        for (Header header : headers) {
            String name = header.getName();
            String value = header.getValue();
            if (name.toLowerCase().equals("content-type")) {
                name = Exchange.CONTENT_TYPE;
            }
            // use http helper to extract parameter value as it may contain multiple values
            Object extracted = HttpHelper.extractHttpParameterValue(value);
            if (strategy != null && !strategy.applyFilterToExternalHeaders(name, extracted, exchange)) {
                HttpHelper.appendHeader(answer.getHeaders(), name, extracted);
            }
        }

        // preserve headers from in by copying any non existing headers
        // to avoid overriding existing headers with old values
        MessageHelper.copyHeaders(exchange.getIn(), answer, false);
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
        if (httpContext != null) {
            return httpClient.execute(httpRequest, httpContext);
        } else {
            return httpClient.execute(httpRequest);
        }
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
     * Creates the HttpMethod to use to call the remote server, either its GET or POST.
     *
     * @param exchange the exchange
     * @return the created method as either GET or POST
     * @throws URISyntaxException is thrown if the URI is invalid
     * @throws Exception is thrown if error creating RequestEntity
     */
    protected HttpRequestBase createMethod(Exchange exchange) throws Exception {
        // creating the url to use takes 2-steps
        String url = HttpHelper.createURL(exchange, getEndpoint());
        URI uri = HttpHelper.createURI(exchange, url, getEndpoint());
        // get the url from the uri
        url = uri.toASCIIString();

        // execute any custom url rewrite
        String rewriteUrl = HttpHelper.urlRewrite(exchange, url, getEndpoint(), this);
        if (rewriteUrl != null) {
            // update url and query string from the rewritten url
            url = rewriteUrl;
            uri = new URI(url);
        }

        // create http holder objects for the request
        HttpEntity requestEntity = createRequestEntity(exchange);
        HttpMethods methodToUse = HttpHelper.createMethod(exchange, getEndpoint(), requestEntity != null);
        HttpRequestBase method = methodToUse.createMethod(url);

        LOG.trace("Using URL: {} with method: {}", url, method);

        if (methodToUse.isEntityEnclosing()) {
            ((HttpEntityEnclosingRequestBase) method).setEntity(requestEntity);
            if (requestEntity != null && requestEntity.getContentType() == null) {
                LOG.debug("No Content-Type provided for URL: {} with exchange: {}", url, exchange);
            }
        }

        // there must be a host on the method
        if (method.getURI().getScheme() == null || method.getURI().getHost() == null) {
            throw new IllegalArgumentException("Invalid uri: " + uri
                    + ". If you are forwarding/bridging http endpoints, then enable the bridgeEndpoint option on the endpoint: " + getEndpoint());
        }

        return method;
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
                    String contentTypeString = ExchangeHelper.getContentType(exchange);
                    ContentType contentType = null;
                    if (contentTypeString != null) {
                        contentType = ContentType.parse(contentTypeString);
                    }
                    if (contentTypeString != null && HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT.equals(contentTypeString)) {
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
                            if (contentType != null) {
                                answer = new FileEntity(file, contentType);
                            } else {
                                answer = new FileEntity(file);
                            }
                        }
                    } else if (data instanceof String) {
                        // be a bit careful with String as any type can most likely be converted to String
                        // so we only do an instanceof check and accept String if the body is really a String
                        // do not fallback to use the default charset as it can influence the request
                        // (for example application/x-www-form-urlencoded forms being sent)
                        String charset = IOHelper.getCharsetName(exchange, false);
                        if (charset == null && contentType != null) {
                            // okay try to get the charset from the content-type
                            Charset cs = contentType.getCharset();
                            if (cs != null) {
                                charset = cs.name();
                            }
                        }
                        StringEntity entity = new StringEntity((String) data, charset);
                        if (contentType != null) {
                            entity.setContentType(contentType.toString());
                        }
                        answer = entity;
                    }

                    // fallback as input stream
                    if (answer == null) {
                        // force the body as an input stream since this is the fallback
                        InputStream is = in.getMandatoryBody(InputStream.class);
                        InputStreamEntity entity = new InputStreamEntity(is, -1);
                        if (contentType != null) {
                            entity.setContentType(contentType.toString());
                        }
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
