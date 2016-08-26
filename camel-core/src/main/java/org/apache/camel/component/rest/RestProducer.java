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
package org.apache.camel.component.rest;

import java.net.URLDecoder;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.tools.apt.helper.CollectionStringBuffer;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.URISupport;

/**
 * Rest producer for calling remote REST services.
 */
public class RestProducer extends DefaultAsyncProducer {

    // the producer of the Camel component that is used as the HTTP client to call the REST service
    private AsyncProcessor producer;

    private boolean preapreUriTemplate = true;

    public RestProducer(Endpoint endpoint, Producer producer) {
        super(endpoint);
        this.producer = AsyncProcessorConverterHelper.convert(producer);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // TODO: request bind to consumes context-type
        // TODO: response bind to content-type returned in response
        // TODO: binding
        try {
            prepareExchange(exchange);
            return producer.process(exchange, callback);
        } catch (Throwable e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    @Override
    public RestEndpoint getEndpoint() {
        return (RestEndpoint) super.getEndpoint();
    }

    public boolean isPreapreUriTemplate() {
        return preapreUriTemplate;
    }

    /**
     * Whether to prepare the uri template and replace {key} with values from the exchange, and set
     * as {@link Exchange#HTTP_URI} header with the resolved uri to use instead of uri from endpoint.
     */
    public void setPreapreUriTemplate(boolean preapreUriTemplate) {
        this.preapreUriTemplate = preapreUriTemplate;
    }

    protected void prepareExchange(Exchange exchange) throws Exception {
        boolean hasPath = false;

        // uri template with path parameters resolved
        // uri template may be optional and the user have entered the uri template in the path instead
        String resolvedUriTemplate = getEndpoint().getUriTemplate() != null ? getEndpoint().getUriTemplate() : getEndpoint().getPath();

        if (preapreUriTemplate) {
            if (resolvedUriTemplate.contains("{")) {
                // resolve template and replace {key} with the values form the exchange
                // each {} is a parameter (url templating)
                String[] arr = resolvedUriTemplate.split("\\/");
                CollectionStringBuffer csb = new CollectionStringBuffer("/");
                for (String a : arr) {
                    if (a.startsWith("{") && a.endsWith("}")) {
                        String key = a.substring(1, a.length() - 1);
                        String value = exchange.getIn().getHeader(key, String.class);
                        if (value != null) {
                            hasPath = true;
                            csb.append(value);
                        } else {
                            csb.append(a);
                        }
                    } else {
                        csb.append(a);
                    }
                }
                resolvedUriTemplate = csb.toString();
            }
        }

        // resolve uri parameters
        String query = getEndpoint().getQueryParameters();
        if (query != null) {
            Map<String, Object> params = URISupport.parseQuery(query);
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                Object v = entry.getValue();
                if (v != null) {
                    String a = v.toString();
                    // decode the key as { may be decoded to %NN
                    a = URLDecoder.decode(a, "UTF-8");
                    if (a.startsWith("{") && a.endsWith("}")) {
                        String key = a.substring(1, a.length() - 1);
                        String value = exchange.getIn().getHeader(key, String.class);
                        if (value != null) {
                            params.put(key, value);
                        } else {
                            params.put(entry.getKey(), entry.getValue());
                        }
                    } else {
                        params.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            query = URISupport.createQueryString(params);
        }

        if (query != null) {
            exchange.getIn().setHeader(Exchange.HTTP_QUERY, query);
        }

        if (hasPath) {
            String host = getEndpoint().getHost();
            String basePath = getEndpoint().getUriTemplate() != null ? getEndpoint().getPath() :  null;
            basePath = FileUtil.stripLeadingSeparator(basePath);
            resolvedUriTemplate = FileUtil.stripLeadingSeparator(resolvedUriTemplate);
            // if so us a header for the dynamic uri template so we reuse same endpoint but the header overrides the actual url to use
            String overrideUri;
            if (basePath != null) {
                overrideUri = String.format("%s/%s/%s", host, basePath, resolvedUriTemplate);
            } else {
                overrideUri = String.format("%s/%s", host, resolvedUriTemplate);
            }
            exchange.getIn().setHeader(Exchange.HTTP_URI, overrideUri);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(producer);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopService(producer);
    }

}
