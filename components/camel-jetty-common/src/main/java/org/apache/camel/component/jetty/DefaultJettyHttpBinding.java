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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.http.common.HttpConstants;
import org.apache.camel.http.common.HttpHeaderFilterStrategy;
import org.apache.camel.http.common.HttpHelper;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.http.common.HttpProtocolHeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version
 */
public class DefaultJettyHttpBinding implements JettyHttpBinding {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultJettyHttpBinding.class);
    private HeaderFilterStrategy headerFilterStrategy = new HttpHeaderFilterStrategy();
    private HeaderFilterStrategy httpProtocolHeaderFilterStrategy = new HttpProtocolHeaderFilterStrategy();
    private boolean throwExceptionOnFailure;
    private boolean transferException;
    private boolean allowJavaSerializedObject;
    private String okStatusCodeRange;

    public DefaultJettyHttpBinding() {
    }

    public void populateResponse(Exchange exchange, JettyContentExchange httpExchange) throws Exception {
        int responseCode = httpExchange.getResponseStatus();

        LOG.debug("HTTP responseCode: {}", responseCode);

        Message in = exchange.getIn();
        if (!isThrowExceptionOnFailure()) {
            // if we do not use failed exception then populate response for all response codes
            populateResponse(exchange, httpExchange, in, getHeaderFilterStrategy(), responseCode);
        } else {
            boolean ok = HttpHelper.isStatusCodeOk(responseCode, okStatusCodeRange);
            if (ok) {
                // only populate response for OK response
                populateResponse(exchange, httpExchange, in, getHeaderFilterStrategy(), responseCode);
            } else {
                // operation failed so populate exception to throw
                Exception ex = populateHttpOperationFailedException(exchange, httpExchange, responseCode);
                if (ex != null) {
                    throw ex;
                } else {
                    populateResponse(exchange, httpExchange, in, getHeaderFilterStrategy(), responseCode);
                }
            }
        }
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public boolean isTransferException() {
        return transferException;
    }

    public void setTransferException(boolean transferException) {
        this.transferException = transferException;
    }

    public boolean isAllowJavaSerializedObject() {
        return allowJavaSerializedObject;
    }

    public void setAllowJavaSerializedObject(boolean allowJavaSerializedObject) {
        this.allowJavaSerializedObject = allowJavaSerializedObject;
    }

    public String getOkStatusCodeRange() {
        return okStatusCodeRange;
    }

    public void setOkStatusCodeRange(String okStatusCodeRange) {
        this.okStatusCodeRange = okStatusCodeRange;
    }

    protected void populateResponse(Exchange exchange, JettyContentExchange httpExchange,
                                    Message in, HeaderFilterStrategy strategy, int responseCode) throws IOException {
        Message answer = exchange.getOut();

        answer.setHeader(Exchange.HTTP_RESPONSE_CODE, responseCode);

        // must use response fields to get the http headers as
        // httpExchange.getHeaders() does not work well with multi valued headers
        Map<String, Collection<String>> headers = httpExchange.getResponseHeaders();
        for (Map.Entry<String, Collection<String>> ent : headers.entrySet()) {
            String name = ent.getKey();
            Collection<String> values = ent.getValue();
            for (String value : values) {
                if (name.toLowerCase().equals("content-type")) {
                    name = Exchange.CONTENT_TYPE;
                    exchange.setProperty(Exchange.CHARSET_NAME, IOHelper.getCharsetNameFromContentType(value));
                }
                if (strategy != null && !strategy.applyFilterToExternalHeaders(name, value, exchange)) {
                    HttpHelper.appendHeader(answer.getHeaders(), name, value);
                }
            }
        }
        
        // preserve headers from in by copying any non existing headers
        // to avoid overriding existing headers with old values
        // We also need to apply the httpProtocolHeaderFilterStrategy to filter the http protocol header
        MessageHelper.copyHeaders(exchange.getIn(), answer, httpProtocolHeaderFilterStrategy, false);

        // extract body after headers has been set as we want to ensure content-type from Jetty HttpExchange
        // has been populated first
        answer.setBody(extractResponseBody(exchange, httpExchange));
    }

    protected Exception populateHttpOperationFailedException(Exchange exchange, JettyContentExchange httpExchange,
                                                                                int responseCode) throws IOException {
        HttpOperationFailedException answer;
        String uri = httpExchange.getUrl();
        Map<String, String> headers = getSimpleMap(httpExchange.getResponseHeaders());
        Object responseBody = extractResponseBody(exchange, httpExchange);

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
            Collection<String> loc = httpExchange.getResponseHeaders().get("location");
            if (loc != null && !loc.isEmpty()) {
                String locationHeader = loc.iterator().next();
                answer = new HttpOperationFailedException(uri, responseCode, null, locationHeader, headers, copy);
            } else {
                // no redirect location
                answer = new HttpOperationFailedException(uri, responseCode, null, null, headers, copy);
            }
        } else {
            // internal server error (error code 500)
            answer = new HttpOperationFailedException(uri, responseCode, null, null, headers, copy);
        }

        return answer;
    }

    protected Object extractResponseBody(Exchange exchange, JettyContentExchange httpExchange) throws IOException {
        Map<String, String> headers = getSimpleMap(httpExchange.getResponseHeaders());
        String contentType = headers.get(Exchange.CONTENT_TYPE);

        // if content type is serialized java object, then de-serialize it to a Java object
        if (contentType != null && HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT.equals(contentType)) {
            // only deserialize java if allowed
            if (isAllowJavaSerializedObject() || isTransferException()) {
                try {
                    InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, httpExchange.getResponseContentBytes());
                    return HttpHelper.deserializeJavaObjectFromStream(is, exchange.getContext());
                } catch (Exception e) {
                    throw new RuntimeCamelException("Cannot deserialize body to Java object", e);
                }
            } else {
                // empty body
                return null;
            }
        } else {
            // just grab the raw content body
            return httpExchange.getBody();
        }
    }

    Map<String, String> getSimpleMap(Map<String, Collection<String>> headers) {
        Map<String, String> result = new HashMap<String, String>();
        for (String key : headers.keySet()) {
            Collection<String> valueCol = headers.get(key);
            String value = (valueCol == null) ? null : valueCol.iterator().next();
            result.put(key, value);
        }
        return result;
    }
}
