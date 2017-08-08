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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.ahc.helper.AhcHelper;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.GZIPHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.asynchttpclient.HttpResponseHeaders;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.request.body.generator.BodyGenerator;
import org.asynchttpclient.request.body.generator.ByteArrayBodyGenerator;
import org.asynchttpclient.request.body.generator.FileBodyGenerator;
import org.asynchttpclient.request.body.generator.InputStreamBodyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultAhcBinding implements AhcBinding {

    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    protected HeaderFilterStrategy httpProtocolHeaderFilterStrategy = new HttpProtocolHeaderFilterStrategy();

    public Request prepareRequest(AhcEndpoint endpoint, Exchange exchange) throws CamelExchangeException {
        if (endpoint.isBridgeEndpoint()) {
            exchange.setProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.TRUE);
            // Need to remove the Host key as it should be not used 
            exchange.getIn().getHeaders().remove("host");
        }

        RequestBuilder builder = new RequestBuilder();
        URI uri;
        try {
            // creating the url to use takes 2-steps
            String url = AhcHelper.createURL(exchange, endpoint);
            uri = AhcHelper.createURI(exchange, url, endpoint);
            // get the url from the uri
            url = uri.toASCIIString();

            log.trace("Setting url {}", url);
            builder.setUrl(url);
        } catch (Exception e) {
            throw new CamelExchangeException("Error creating URL", exchange, e);
        }
        String method = extractMethod(exchange);
        log.trace("Setting method {}", method);
        builder.setMethod(method);

        populateHeaders(builder, endpoint, exchange);
        populateCookieHeaders(builder, endpoint, exchange, uri);
        populateBody(builder, endpoint, exchange);

        return builder.build();
    }

    protected String extractMethod(Exchange exchange) {
        // prefer method from header
        String method = exchange.getIn().getHeader(Exchange.HTTP_METHOD, String.class);
        if (method != null) {
            return method;
        }

        // if there is a body then do a POST otherwise a GET
        boolean hasBody = exchange.getIn().getBody() != null;
        return hasBody ? "POST" : "GET";
    }

    protected void populateHeaders(RequestBuilder builder, AhcEndpoint endpoint, Exchange exchange) {
        HeaderFilterStrategy strategy = endpoint.getHeaderFilterStrategy();

        // propagate headers as HTTP headers
        for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
            String headerValue = exchange.getIn().getHeader(entry.getKey(), String.class);
            if (strategy != null && !strategy.applyFilterToCamelHeaders(entry.getKey(), headerValue, exchange)) {
                if (log.isTraceEnabled()) {
                    log.trace("Adding header {} = {}", entry.getKey(), headerValue);
                }
                builder.addHeader(entry.getKey(), headerValue);
            }
        }
        
        if (endpoint.isConnectionClose()) {
            builder.addHeader("Connection", "close");
        }
    }

    private void populateCookieHeaders(RequestBuilder builder, AhcEndpoint endpoint, Exchange exchange, URI uri) throws CamelExchangeException {
        if (endpoint.getCookieHandler() != null) {
            try {
                Map<String, List<String>> cookieHeaders = endpoint.getCookieHandler().loadCookies(exchange, uri);
                for (Map.Entry<String, List<String>> entry : cookieHeaders.entrySet()) {
                    String key = entry.getKey();
                    for (String value : entry.getValue()) {
                        if (log.isTraceEnabled()) {
                            log.trace("Adding header {} = {}", key, value);
                        }
                        builder.addHeader(key, value);                        
                    }
                }
            } catch (IOException e) {
                throw new CamelExchangeException("Error loading cookies", exchange, e);
            }
        }
    }

    protected void populateBody(RequestBuilder builder, AhcEndpoint endpoint, Exchange exchange) throws CamelExchangeException {
        Message in = exchange.getIn();
        if (in.getBody() == null) {
            return;
        }

        String contentType = ExchangeHelper.getContentType(exchange);
        BodyGenerator body = in.getBody(BodyGenerator.class);
        String charset = IOHelper.getCharsetName(exchange, false);

        if (body == null) {
            try {
                Object data = in.getBody();
                if (data != null) {
                    if (contentType != null && AhcConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT.equals(contentType)) {

                        if (!endpoint.getComponent().isAllowJavaSerializedObject()) {
                            throw new CamelExchangeException("Content-type " + AhcConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT + " is not allowed", exchange);
                        }

                        // serialized java object
                        Serializable obj = in.getMandatoryBody(Serializable.class);
                        // write object to output stream
                        ByteArrayOutputStream bos = new ByteArrayOutputStream(endpoint.getBufferSize());
                        AhcHelper.writeObjectToStream(bos, obj);
                        byte[] bytes = bos.toByteArray();
                        body = new ByteArrayBodyGenerator(bytes);
                        IOHelper.close(bos);
                    } else if (data instanceof File || data instanceof GenericFile) {
                        // file based (could potentially also be a FTP file etc)
                        File file = in.getBody(File.class);
                        if (file != null) {
                            body = new FileBodyGenerator(file);
                        }
                    } else if (data instanceof String) {
                        // be a bit careful with String as any type can most likely be converted to String
                        // so we only do an instanceof check and accept String if the body is really a String
                        // do not fallback to use the default charset as it can influence the request
                        // (for example application/x-www-form-urlencoded forms being sent)
                        if (charset != null) {
                            body = new ByteArrayBodyGenerator(((String) data).getBytes(charset));
                        } else {
                            body = new ByteArrayBodyGenerator(((String) data).getBytes());
                        }
                    }
                    // fallback as input stream
                    if (body == null) {
                        // force the body as an input stream since this is the fallback
                        InputStream is = in.getMandatoryBody(InputStream.class);
                        body = new InputStreamBodyGenerator(is);
                    }
                }
            } catch (UnsupportedEncodingException e) {
                throw new CamelExchangeException("Error creating BodyGenerator from message body", exchange, e);
            } catch (IOException e) {
                throw new CamelExchangeException("Error serializing message body", exchange, e);
            }
        }

        if (body != null) {
            log.trace("Setting body {}", body);
            builder.setBody(body);
        }
        if (charset != null) {
            log.trace("Setting body charset {}", charset);
            builder.setCharset(Charset.forName(charset));
        }
        // must set content type, even if its null, otherwise it may default to
        // application/x-www-form-urlencoded which may not be your intention
        log.trace("Setting Content-Type {}", contentType);
        if (ObjectHelper.isNotEmpty(contentType)) {
            builder.setHeader(Exchange.CONTENT_TYPE, contentType);
        }
    }

    @Override
    public void onThrowable(AhcEndpoint endpoint, Exchange exchange, Throwable t) throws Exception {
        exchange.setException(t);
    }

    @Override
    public void onStatusReceived(AhcEndpoint endpoint, Exchange exchange, HttpResponseStatus responseStatus) throws Exception {
        // preserve headers from in by copying any non existing headers
        // to avoid overriding existing headers with old values
        // Just filter the http protocol headers 
        MessageHelper.copyHeaders(exchange.getIn(), exchange.getOut(), httpProtocolHeaderFilterStrategy, false);
        exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, responseStatus.getStatusCode());
        exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_TEXT, responseStatus.getStatusText());
    }

    @Override
    public void onHeadersReceived(AhcEndpoint endpoint, Exchange exchange, HttpResponseHeaders headers) throws Exception {
        Map<String, List<String>> m = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        for (Entry<String, String> entry : headers.getHeaders().entries()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (!m.containsKey(key)) {
                m.put(key, new LinkedList<String>());
                exchange.getOut().getHeaders().put(key, value);
            }
            m.get(key).add(value);
        }
        // handle cookies
        if (endpoint.getCookieHandler() != null) {
            try {
                // creating the url to use takes 2-steps
                String url = AhcHelper.createURL(exchange, endpoint);
                URI uri = AhcHelper.createURI(exchange, url, endpoint);
                endpoint.getCookieHandler().storeCookies(exchange, uri, m);
            } catch (Exception e) {
                throw new CamelExchangeException("Error storing cookies", exchange, e);
            }
        }
    }

    @Override
    public void onComplete(AhcEndpoint endpoint, Exchange exchange, String url, ByteArrayOutputStream os, int contentLength,
                           int statusCode, String statusText) throws Exception {
        // copy from output stream to input stream
        os.flush();
        os.close();
        InputStream is = new ByteArrayInputStream(os.toByteArray());

        String contentEncoding = exchange.getOut().getHeader(Exchange.CONTENT_ENCODING, String.class);
        if (!exchange.getProperty(Exchange.SKIP_GZIP_ENCODING, Boolean.FALSE, Boolean.class)) {
            is = GZIPHelper.uncompressGzip(contentEncoding, is);
        }

        // Honor the character encoding
        String contentType = exchange.getOut().getHeader(Exchange.CONTENT_TYPE, String.class);
        if (contentType != null) {
            // find the charset and set it to the Exchange
            AhcHelper.setCharsetFromContentType(contentType, exchange);
        }

        Object body = is;
        // if content type is a serialized java object then de-serialize it back to a Java object but only if its allowed
        // an exception can also be transffered as java object
        if (contentType != null && contentType.equals(AhcConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT)) {
            if (endpoint.getComponent().isAllowJavaSerializedObject() || endpoint.isTransferException()) {
                body = AhcHelper.deserializeJavaObjectFromStream(is);
            }
        }

        if (!endpoint.isThrowExceptionOnFailure()) {
            // if we do not use failed exception then populate response for all response codes
            populateResponse(exchange, body, contentLength, statusCode);
        } else {
            if (statusCode >= 100 && statusCode < 300) {
                // only populate response for OK response
                populateResponse(exchange, body, contentLength, statusCode);
            } else {
                // operation failed so populate exception to throw
                throw populateHttpOperationFailedException(endpoint, exchange, url, body, contentLength, statusCode, statusText);
            }
        }
    }

    private Exception populateHttpOperationFailedException(AhcEndpoint endpoint, Exchange exchange, String url,
                                                           Object body, int contentLength,
                                                           int statusCode, String statusText) {
        Exception answer;

        if (endpoint.isTransferException() && body != null && body instanceof Exception) {
            // if the response was a serialized exception then use that
            return (Exception) body;
        }

        // make a defensive copy of the response body in the exception so its detached from the cache
        String copy = null;
        if (body != null) {
            copy = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, body);
        }

        Map<String, String> headers = extractResponseHeaders(exchange);

        if (statusCode >= 300 && statusCode < 400) {
            String redirectLocation = exchange.getOut().getHeader("Location", String.class);
            if (redirectLocation != null) {
                answer = new AhcOperationFailedException(url, statusCode, statusText, redirectLocation, headers, copy);
            } else {
                // no redirect location
                answer = new AhcOperationFailedException(url, statusCode, statusText, null, headers, copy);
            }
        } else {
            // internal server error (error code 500)
            answer = new AhcOperationFailedException(url, statusCode, statusText, null, headers, copy);
        }

        return answer;
    }

    private Map<String, String> extractResponseHeaders(Exchange exchange) {
        Map<String, String> answer = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Object> entry : exchange.getOut().getHeaders().entrySet()) {
            String key = entry.getKey();
            String value = exchange.getContext().getTypeConverter().convertTo(String.class, entry.getValue());
            if (value != null) {
                answer.put(key, value);
            }
        }
        return answer;
    }

    private void populateResponse(Exchange exchange, Object body, int contentLength, int responseCode) {
        exchange.getOut().setBody(body);
        exchange.getOut().setHeader(Exchange.CONTENT_LENGTH, contentLength);
    }
}
