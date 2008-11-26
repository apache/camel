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

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.http.helper.LoadingByteArrayOutputStream;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.apache.camel.component.http.HttpMethods.HTTP_METHOD;

/**
 * @version $Revision$
 */
public class HttpProducer extends DefaultProducer<HttpExchange> implements Producer<HttpExchange> {
    public static final String HTTP_URI = "http.uri";
    public static final String HTTP_RESPONSE_CODE = "http.responseCode";
    public static final String QUERY = "org.apache.camel.component.http.query";    
    // This should be a set of lower-case strings
    @Deprecated
    public static final Set<String> HEADERS_TO_SKIP =
        new HashSet<String>(Arrays.asList("content-length", "content-type", HTTP_RESPONSE_CODE.toLowerCase()));
    private static final transient Log LOG = LogFactory.getLog(HttpProducer.class);
    private HttpClient httpClient;

    public HttpProducer(HttpEndpoint endpoint) {
        super(endpoint);
        httpClient = endpoint.createHttpClient();
    }

    public void process(Exchange exchange) throws Exception {
        HttpMethod method = createMethod(exchange);
        Message in = exchange.getIn();
        HeaderFilterStrategy strategy = ((HttpEndpoint)getEndpoint()).getHeaderFilterStrategy();

        // propagate headers as HTTP headers
        for (String headerName : in.getHeaders().keySet()) {
            String headerValue = in.getHeader(headerName, String.class);
            if (strategy != null && !strategy.applyFilterToCamelHeaders(headerName, headerValue)) {
                method.addRequestHeader(headerName, headerValue);
            }
        }

        // lets store the result in the output message.
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Executing http " + method.getName() + " method: " + method.getURI().toString());
            }
            int responseCode = executeMethod(method);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Http responseCode: " + responseCode);
            }

            if (responseCode >= 100 && responseCode < 300) {
                Message answer = exchange.getOut(true);

                answer.setHeaders(in.getHeaders());
                answer.setHeader(HTTP_RESPONSE_CODE, responseCode);
                answer.setBody(extractResponseBody(method));

                // propagate HTTP response headers
                Header[] headers = method.getResponseHeaders();
                for (Header header : headers) {
                    String name = header.getName();
                    String value = header.getValue();
                    if (strategy != null && !strategy.applyFilterToExternalHeaders(name, value)) {
                        answer.setHeader(name, value);
                    }
                }
            } else {
                HttpOperationFailedException exception = null;
                Header[] headers = method.getResponseHeaders();
                InputStream is =  extractResponseBody(method);
                if (responseCode >= 300 && responseCode < 400) {
                    String redirectLocation;
                    Header locationHeader = method.getResponseHeader("location");
                    if (locationHeader != null) {
                        redirectLocation = locationHeader.getValue();
                        exception = new HttpOperationFailedException(responseCode, method.getStatusLine(), redirectLocation, headers, is);
                    } else {
                        // no redirect location
                        exception = new HttpOperationFailedException(responseCode, method.getStatusLine(), headers, is);
                    }
                } else {
                    // internal server error (error code 500)
                    exception = new HttpOperationFailedException(responseCode, method.getStatusLine(), headers, is);
                }

                if (exception != null) {                    
                    throw exception;
                }
            }

        } finally {
            method.releaseConnection();
        }
    }

    /**
     * Strategy when executing the method (calling the remote server).
     *
     * @param method    the method to execute
     * @return the response code
     * @throws IOException can be thrown
     */
    protected int executeMethod(HttpMethod method) throws IOException {
        return httpClient.executeMethod(method);
    }

    /**
     * Extracts the response from the method as a InputStream.
     *
     * @param method  the method that was executed
     * @return  the response as a stream
     * @throws IOException can be thrown
     */
    protected static InputStream extractResponseBody(HttpMethod method) throws IOException {
        LoadingByteArrayOutputStream bos = null;
        InputStream is = null;
        try {
            bos = new LoadingByteArrayOutputStream();
            is = method.getResponseBodyAsStream();
            // in case of no response stream
            if (is == null) {
                return null;
            }
            IOUtils.copy(is, bos);
            bos.flush();
            return bos.createInputStream();
        } finally {
            ObjectHelper.close(is, "Extracting response body", LOG);
            ObjectHelper.close(bos, "Extracting response body", LOG);
        }
    }

    /**
     * Creates the HttpMethod to use to call the remote server, either its GET or POST.
     *
     * @param exchange  the exchange
     * @return the created method as either GET or POST
     */
    protected HttpMethod createMethod(Exchange exchange) {
        // is a query string provided in the endpoint URI or in a header (header overrules endpoint)
        String queryString = exchange.getIn().getHeader(QUERY, String.class);
        if (queryString == null) {
            queryString = ((HttpEndpoint)getEndpoint()).getHttpUri().getQuery();
        }
        RequestEntity requestEntity = createRequestEntity(exchange);

        // compute what method to use either GET or POST
        HttpMethods methodToUse;
        HttpMethods m = exchange.getIn().getHeader(HTTP_METHOD, HttpMethods.class);
        if (m != null) {
            // always use what end-user provides in a header
            methodToUse = m;
        } else if (queryString != null) {
            // if a query string is provided then use GET
            methodToUse = HttpMethods.GET;
        } else {
            // fallback to POST if data, otherwise GET
            methodToUse = requestEntity != null ? HttpMethods.POST : HttpMethods.GET;
        }

        String uri = exchange.getIn().getHeader(HTTP_URI, String.class);
        if (uri == null) {
            uri = ((HttpEndpoint)getEndpoint()).getHttpUri().toString();
        }

        HttpMethod method = methodToUse.createMethod(uri);

        if (queryString != null) {
            method.setQueryString(queryString);
        }
        if (methodToUse.isEntityEnclosing()) {
            ((EntityEnclosingMethod)method).setRequestEntity(requestEntity);
        }

        return method;
    }

    /**
     * Creates a holder object for the data to send to the remote server.
     *
     * @param exchange  the exchange with the IN message with data to send
     * @return the data holder
     */
    protected RequestEntity createRequestEntity(Exchange exchange) {
        Message in = exchange.getIn();
        if (in.getBody() == null) {
            return null;
        }
        try {
            return in.getBody(RequestEntity.class);
        } catch (NoTypeConversionAvailableException ex) {
            try {
                String data = in.getBody(String.class);
                if (data != null) {
                    String contentType = in.getHeader("Content-Type", String.class);
                    String charset = exchange.getProperty(Exchange.CHARSET_NAME, String.class);
                    return new StringRequestEntity(data, contentType, charset);
                } else {
                    // no data
                    return null;
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeCamelException(e);
            }
        }
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

}
