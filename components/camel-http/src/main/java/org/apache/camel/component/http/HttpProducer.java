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
package org.apache.camel.component.http;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.http.common.HttpConstants;
import org.apache.camel.http.common.HttpHelper;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.http.common.HttpProtocolHeaderFilterStrategy;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.GZIPHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.FileRequestEntity;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class HttpProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(HttpProducer.class);
    private HttpClient httpClient;
    private boolean throwException;
    private boolean transferException;
    private HeaderFilterStrategy httpProtocolHeaderFilterStrategy = new HttpProtocolHeaderFilterStrategy();

    public HttpProducer(HttpEndpoint endpoint) {
        super(endpoint);
        this.httpClient = endpoint.createHttpClient();
        this.throwException = endpoint.isThrowExceptionOnFailure();
        this.transferException = endpoint.isTransferException();
    }

    public void process(Exchange exchange) throws Exception {
        // if we bridge endpoint then we need to skip matching headers with the HTTP_QUERY to avoid sending
        // duplicated headers to the receiver, so use this skipRequestHeaders as the list of headers to skip
        Map<String, Object> skipRequestHeaders = null;

        if (getEndpoint().isBridgeEndpoint()) {
            exchange.setProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.TRUE);
            String queryString = exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class);
            if (queryString != null) {
                skipRequestHeaders = URISupport.parseQuery(queryString, false, true);
            }
            // Need to remove the Host key as it should be not used 
            exchange.getIn().getHeaders().remove("host");
        }
        HttpMethod method = createMethod(exchange);
        Message in = exchange.getIn();
        String httpProtocolVersion = in.getHeader(Exchange.HTTP_PROTOCOL_VERSION, String.class);
        if (httpProtocolVersion != null) {
            // set the HTTP protocol version
            HttpMethodParams params = method.getParams();
            params.setVersion(HttpVersion.parse(httpProtocolVersion));
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
                    method.addRequestHeader(key, s);
                }
            }
        }
        
        if (getEndpoint().getCookieHandler() != null) {
            // disable the per endpoint cookie handling
            method.getParams().setCookiePolicy(CookiePolicy.IGNORE_COOKIES);
            Map<String, List<String>> cookieHeaders = getEndpoint().getCookieHandler().loadCookies(exchange, new URI(method.getURI().getEscapedURI()));
            for (Map.Entry<String, List<String>> entry : cookieHeaders.entrySet()) {
                String key = entry.getKey();
                if (entry.getValue().size() > 0) {
                    // use the default toString of a ArrayList to create in the form [xxx, yyy]
                    // if multi valued, for a single value, then just output the value as is
                    String s = entry.getValue().size() > 1 ? entry.getValue().toString() : entry.getValue().get(0);
                    method.addRequestHeader(key, s);
                }
            }
        }

        if (getEndpoint().isConnectionClose()) {
            method.addRequestHeader("Connection", "close");
        }

        // lets store the result in the output message.
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Executing http {} method: {}", method.getName(), method.getURI().toString());
            }
            int responseCode = executeMethod(method);
            LOG.debug("Http responseCode: {}", responseCode);

            if (!throwException) {
                // if we do not use failed exception then populate response for all response codes
                populateResponse(exchange, method, in, strategy, responseCode);
            } else {
                boolean ok = HttpHelper.isStatusCodeOk(responseCode, getEndpoint().getOkStatusCodeRange());
                if (ok) {
                    // only populate response for OK response
                    populateResponse(exchange, method, in, strategy, responseCode);
                } else {
                    // operation failed so populate exception to throw
                    throw populateHttpOperationFailedException(exchange, method, responseCode);
                }
            }
        } finally {
            method.releaseConnection();
        }
    }

    @Override
    public HttpEndpoint getEndpoint() {
        return (HttpEndpoint) super.getEndpoint();
    }

    protected void populateResponse(Exchange exchange, HttpMethod method, Message in, HeaderFilterStrategy strategy, int responseCode) throws IOException, ClassNotFoundException, URISyntaxException {
        //We just make the out message is not create when extractResponseBody throws exception,
        Object response = extractResponseBody(method, exchange, getEndpoint().isIgnoreResponseBody());
        Message answer = exchange.getOut();

        answer.setHeader(Exchange.HTTP_RESPONSE_CODE, responseCode);
        answer.setHeader(Exchange.HTTP_RESPONSE_TEXT, method.getStatusText());
        answer.setBody(response);

        // propagate HTTP response headers
        Header[] headers = method.getResponseHeaders();
        Map<String, List<String>> m = new HashMap<String, List<String>>();
        for (Header header : headers) {
            String name = header.getName();
            String value = header.getValue();
            m.put(name, Collections.singletonList(value));
            if (name.toLowerCase().equals("content-type")) {
                name = Exchange.CONTENT_TYPE;
                exchange.setProperty(Exchange.CHARSET_NAME, IOHelper.getCharsetNameFromContentType(value));
            }
            
            // use http helper to extract parameter value as it may contain multiple values
            Object extracted = HttpHelper.extractHttpParameterValue(value);
            if (strategy != null && !strategy.applyFilterToExternalHeaders(name, extracted, exchange)) {
                HttpHelper.appendHeader(answer.getHeaders(), name, extracted);
            }
        }
        // handle cookies
        if (getEndpoint().getCookieHandler() != null) {
            getEndpoint().getCookieHandler().storeCookies(exchange, new URI(method.getURI().getEscapedURI()), m);
        }
        // endpoint might be configured to copy headers from in to out
        // to avoid overriding existing headers with old values just
        // filter the http protocol headers
        if (getEndpoint().isCopyHeaders()) {
            MessageHelper.copyHeaders(exchange.getIn(), answer, httpProtocolHeaderFilterStrategy, false);
        }
    }

    protected Exception populateHttpOperationFailedException(Exchange exchange, HttpMethod method, int responseCode) throws IOException, ClassNotFoundException, URISyntaxException {
        Exception answer;

        String uri = method.getURI().toString();
        String statusText = method.getStatusLine() != null ? method.getStatusLine().getReasonPhrase() : null;
        Map<String, String> headers = extractResponseHeaders(method.getResponseHeaders());
        // handle cookies
        if (getEndpoint().getCookieHandler() != null) {
            Map<String, List<String>> m = new HashMap<String, List<String>>();
            for (Entry<String, String> e : headers.entrySet()) {
                m.put(e.getKey(), Collections.singletonList(e.getValue()));
            }
            getEndpoint().getCookieHandler().storeCookies(exchange, new URI(method.getURI().getEscapedURI()), m);
        }

        Object responseBody = extractResponseBody(method, exchange, getEndpoint().isIgnoreResponseBody());
        if (transferException && responseBody != null && responseBody instanceof Exception) {
            // if the response was a serialized exception then use that
            return (Exception) responseBody;
        }

        // make a defensive copy of the response body in the exception so its detached from the cache
        String copy = null;
        if (responseBody != null) {
            copy = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, responseBody);
        }

        if (responseCode >= 300 && responseCode < 400) {
            String redirectLocation;
            Header locationHeader = method.getResponseHeader("location");
            if (locationHeader != null) {
                redirectLocation = locationHeader.getValue();
                answer = new HttpOperationFailedException(uri, responseCode, statusText, redirectLocation, headers, copy);
            } else {
                // no redirect location
                answer = new HttpOperationFailedException(uri, responseCode, statusText, null, headers, copy);
            }
        } else {
            // internal server error (error code 500)
            answer = new HttpOperationFailedException(uri, responseCode, statusText, null, headers, copy);
        }

        return answer;
    }

    /**
     * Strategy when executing the method (calling the remote server).
     *
     * @param method the method to execute
     * @return the response code
     * @throws IOException can be thrown
     */
    protected int executeMethod(HttpMethod method) throws IOException {
        return httpClient.executeMethod(method);
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
     * @param method the method that was executed
     * @param ignoreResponseBody if it is true, camel don't read the response and cached the input stream
     * @return the response either as a stream, or as a deserialized java object
     * @throws IOException can be thrown
     */
    protected Object extractResponseBody(HttpMethod method, Exchange exchange, boolean ignoreResponseBody) throws IOException, ClassNotFoundException {
        InputStream is = method.getResponseBodyAsStream();
        if (is == null) {
            return null;
        }

        Header header = method.getResponseHeader(Exchange.CONTENT_ENCODING);
        String contentEncoding = header != null ? header.getValue() : null;

        if (!exchange.getProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.FALSE, Boolean.class)) {
            is = GZIPHelper.uncompressGzip(contentEncoding, is);
        }

        // Honor the character encoding
        String contentType = null;
        header = method.getResponseHeader("content-type");
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
                // wrap the response in a stream cache so its re-readable
                InputStream response = null;
                if (!ignoreResponseBody) {
                    response = doExtractResponseBodyAsStream(is, exchange);
                }
                return response;
            } else {
                // use the response stream as-is
                return is;
            }
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
     * @throws CamelExchangeException is thrown if error creating RequestEntity
     */
    @SuppressWarnings("deprecation")
    protected HttpMethod createMethod(Exchange exchange) throws Exception {
        // creating the url to use takes 2-steps
        String url = HttpHelper.createURL(exchange, getEndpoint());
        URI uri = HttpHelper.createURI(exchange, url, getEndpoint());
        // get the url and query string from the uri
        url = uri.toASCIIString();
        String queryString = uri.getRawQuery();

        // execute any custom url rewrite
        String rewriteUrl = HttpHelper.urlRewrite(exchange, url, getEndpoint(), this);
        if (rewriteUrl != null) {
            // update url and query string from the rewritten url
            url = rewriteUrl;
            uri = new URI(url);
            // use raw query to have uri decimal encoded which http client requires
            queryString = uri.getRawQuery();
        }

        // remove query string as http client does not accept that
        if (url.indexOf('?') != -1) {
            url = url.substring(0, url.indexOf('?'));
        }

        // create http holder objects for the request
        RequestEntity requestEntity = createRequestEntity(exchange);
        String methodName = HttpHelper.createMethod(exchange, getEndpoint(), requestEntity != null).name();
        HttpMethods methodsToUse = HttpMethods.valueOf(methodName);
        HttpMethod method = methodsToUse.createMethod(url);
        if (queryString != null) {
            // need to encode query string
            queryString = UnsafeUriCharactersEncoder.encode(queryString);
            method.setQueryString(queryString);
        }

        LOG.trace("Using URL: {} with method: {}", url, method);

        if (methodsToUse.isEntityEnclosing()) {
            ((EntityEnclosingMethod) method).setRequestEntity(requestEntity);
            if (requestEntity != null && requestEntity.getContentType() == null) {
                LOG.debug("No Content-Type provided for URL: {} with exchange: {}", url, exchange);
            }
        }

        // there must be a host on the method
        if (method.getHostConfiguration().getHost() == null) {
            throw new IllegalArgumentException("Invalid uri: " + url
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
    protected RequestEntity createRequestEntity(Exchange exchange) throws CamelExchangeException {
        Message in = exchange.getIn();
        if (in.getBody() == null) {
            return null;
        }

        RequestEntity answer = in.getBody(RequestEntity.class);
        if (answer == null) {
            try {
                Object data = in.getBody();
                if (data != null) {
                    String contentType = ExchangeHelper.getContentType(exchange);

                    if (contentType != null && HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT.equals(contentType)) {
                        if (!getEndpoint().getComponent().isAllowJavaSerializedObject()) {
                            throw new CamelExchangeException("Content-type " + HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT + " is not allowed", exchange);
                        }
                        // serialized java object
                        Serializable obj = in.getMandatoryBody(Serializable.class);
                        // write object to output stream
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        HttpHelper.writeObjectToStream(bos, obj);
                        answer = new ByteArrayRequestEntity(bos.toByteArray(), HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT);
                        IOHelper.close(bos);
                    } else if (data instanceof File || data instanceof GenericFile) {
                        // file based (could potentially also be a FTP file etc)
                        File file = in.getBody(File.class);
                        if (file != null) {
                            answer = new FileRequestEntity(file, contentType);
                        }
                    } else if (data instanceof String) {
                        // be a bit careful with String as any type can most likely be converted to String
                        // so we only do an instanceof check and accept String if the body is really a String
                        // do not fallback to use the default charset as it can influence the request
                        // (for example application/x-www-form-urlencoded forms being sent)
                        String charset = IOHelper.getCharsetName(exchange, false);
                        answer = new StringRequestEntity((String) data, contentType, charset);
                    }
                    // fallback as input stream
                    if (answer == null) {
                        // force the body as an input stream since this is the fallback
                        InputStream is = in.getMandatoryBody(InputStream.class);
                        answer = new InputStreamRequestEntity(is, contentType);
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
