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
package org.apache.camel.swagger.producer;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.util.ObjectHelper;

public class DummyRestProducerFactory implements RestProducerFactory {

    @Override
    public Producer createProducer(CamelContext camelContext, String host,
                            String verb, String basePath, final String uriTemplate, String queryParameters,
                            String consumes, String produces, Map<String, Object> parameters) throws Exception {

        // use a dummy endpoint
        Endpoint endpoint = camelContext.getEndpoint("stub:dummy");

        return new DefaultProducer(endpoint) {
            @Override
            public void process(Exchange exchange) throws Exception {
                String query = exchange.getIn().getHeader(Exchange.REST_HTTP_QUERY, String.class);
                if (query != null) {
                    String name = ObjectHelper.after(query, "name=");
                    exchange.getIn().setBody("Bye " + name);
                }
                String uri = exchange.getIn().getHeader(Exchange.REST_HTTP_URI, String.class);
                if (uri != null) {
                    int pos = uri.lastIndexOf('/');
                    String name = uri.substring(pos + 1);
                    exchange.getIn().setBody("Hello " + name);
                }
            }
        };
    }
}
