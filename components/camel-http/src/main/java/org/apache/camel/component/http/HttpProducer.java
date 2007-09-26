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

import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;

/**
 * @version $Revision: 1.1 $
 */
public class HttpProducer extends DefaultProducer<HttpExchange> implements Producer<HttpExchange> {
    public static final String QUERY = "org.apache.camel.component.http.query";
    private HttpClient httpClient = new HttpClient();

    public HttpProducer(HttpEndpoint endpoint) {
        super(endpoint);
    }

    public void process(Exchange exchange) throws Exception {
        HttpMethod method = createMethod(exchange);
        int responseCode = httpClient.executeMethod(method);

        // lets store the result in the output message.
        InputStream in = method.getResponseBodyAsStream();
        Message out = exchange.getOut(true);
        out.setBody(in);

        // lets set the headers
        Header[] headers = method.getResponseHeaders();
        for (Header header : headers) {
            String name = header.getName();
            String value = header.getValue();
            out.setHeader(name, value);
        }

        out.setHeader("http.responseCode", responseCode);
    }

    protected HttpMethod createMethod(Exchange exchange) {
        String uri = ((HttpEndpoint)getEndpoint()).getHttpUri().toString();
        RequestEntity requestEntity = createRequestEntity(exchange);
        if (requestEntity == null) {
            GetMethod method = new GetMethod(uri);
            if (exchange.getIn().getHeader(QUERY) != null){
                method.setQueryString(exchange.getIn().getHeader(QUERY, String.class));
            }
            return method;
        }
        // TODO we might be PUT? - have some better way to explicitly choose
        // method
        PostMethod method = new PostMethod(uri);
        method.setRequestEntity(requestEntity);
        return method;
    }

    protected RequestEntity createRequestEntity(Exchange exchange) {
        Message in = exchange.getIn();
        RequestEntity entity = in.getBody(RequestEntity.class);
        if (entity == null) {
            byte[] data = in.getBody(byte[].class);
            if (data == null) {
                return null;
            }
            String contentType = in.getHeader("Content-Type", String.class);
            if (contentType != null) {
                return new ByteArrayRequestEntity(data, contentType);
            } else {
                return new ByteArrayRequestEntity(data);
            }
        }
        return entity;
    }
}
