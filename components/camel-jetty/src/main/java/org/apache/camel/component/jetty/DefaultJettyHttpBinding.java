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
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.http.HttpConstants;
import org.apache.camel.component.http.HttpHeaderFilterStrategy;
import org.apache.camel.component.http.HttpOperationFailedException;
import org.apache.camel.component.http.helper.HttpHelper;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision$
 */
public class DefaultJettyHttpBinding implements JettyHttpBinding {

    private static final transient Log LOG = LogFactory.getLog(DefaultJettyHttpBinding.class);
    private HeaderFilterStrategy headerFilterStrategy = new HttpHeaderFilterStrategy();
    private boolean throwExceptionOnFailure;
    private boolean transferException;

    public void populateResponse(Exchange exchange, JettyContentExchange httpExchange) throws Exception {
        int responseCode = httpExchange.getResponseStatus();

        if (LOG.isDebugEnabled()) {
            LOG.debug("HTTP responseCode: " + responseCode);
        }

        Message in = exchange.getIn();
        if (!isThrowExceptionOnFailure()) {
            // if we do not use failed exception then populate response for all response codes
            populateResponse(exchange, httpExchange, in, getHeaderFilterStrategy(), responseCode);
        } else {
            if (responseCode >= 100 && responseCode < 300) {
                // only populate response for OK response
                populateResponse(exchange, httpExchange, in, getHeaderFilterStrategy(), responseCode);
            } else {
                // operation failed so populate exception to throw
                throw populateHttpOperationFailedException(exchange, httpExchange, responseCode);
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

    protected void populateResponse(Exchange exchange, JettyContentExchange httpExchange,
                                    Message in, HeaderFilterStrategy strategy, int responseCode) throws IOException {
        Message answer = exchange.getOut();

        answer.setHeaders(in.getHeaders());
        answer.setHeader(Exchange.HTTP_RESPONSE_CODE, responseCode);

        // propagate HTTP response headers
        // must use entrySet to ensure case of keys is preserved
        for (Map.Entry<String, String> entry : httpExchange.getHeaders().entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (name.toLowerCase().equals("content-type")) {
                name = Exchange.CONTENT_TYPE;
            }
            if (strategy != null && !strategy.applyFilterToExternalHeaders(name, value, exchange)) {
                answer.setHeader(name, value);
            }
        }

        // extract body after headers has been set as we want to ensure content-type from Jetty HttpExchange
        // has been populated first
        answer.setBody(extractResponseBody(exchange, httpExchange));
    }

    protected Exception populateHttpOperationFailedException(Exchange exchange, JettyContentExchange httpExchange,
                                                                                int responseCode) throws IOException {
        HttpOperationFailedException answer;
        String uri = httpExchange.getUrl();
        Map<String, String> headers = httpExchange.getHeaders();
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
            String locationHeader = httpExchange.getResponseFields().getStringField("location");
            if (locationHeader != null) {
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
        String contentType = httpExchange.getHeaders().get(Exchange.CONTENT_TYPE);

        // if content type is serialized java object, then de-serialize it to a Java object
        if (contentType != null && HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT.equals(contentType)) {
            try {
                InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, httpExchange.getResponseContentBytes());
                return HttpHelper.deserializeJavaObjectFromStream(is);
            } catch (Exception e) {
                throw new RuntimeCamelException("Cannot deserialize body to Java object", e);
            }
        } else {
            // just grab the content as string
            return httpExchange.getBody();
        }
    }

}
