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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.processor.SetHeaderProcessor;
import org.apache.camel.runtimecatalog.RuntimeCamelCatalog;
import org.apache.camel.spi.SendDynamicAware;
import org.apache.camel.util.URISupport;

public class HttpSendDynamicAware implements SendDynamicAware {

    // TODO: Move to camel-http-core

    private String scheme;

    @Override
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    @Override
    public String getScheme() {
        return scheme;
    }

    @Override
    public Processor createPreProcessor(Exchange exchange, Object recipient) throws Exception {
        RuntimeCamelCatalog catalog = exchange.getContext().getRuntimeCamelCatalog();
        Map<String, String> lenient = catalog.endpointLenientProperties(recipient.toString());
        if (!lenient.isEmpty()) {
            // all lenient properties can be dynamic
            String query = URISupport.createQueryString(new LinkedHashMap<>(lenient));
            return new SetHeaderProcessor(ExpressionBuilder.constantExpression(Exchange.HTTP_QUERY), ExpressionBuilder.constantExpression(query));
        }
        return null;
    }

    @Override
    public Processor createPostProcessor(Exchange exchange, Object recipient) throws Exception {
        // no need to cleanup
        return null;
    }

    @Override
    public String resolveStaticUri(Exchange exchange, Object recipient) throws Exception {
        RuntimeCamelCatalog catalog = exchange.getContext().getRuntimeCamelCatalog();
        Map<String, String> lenient = catalog.endpointLenientProperties(recipient.toString());
        if (!lenient.isEmpty()) {
            // all lenient properties can be dynamic, and therefore build a new static uri without lenient options
            Map<String, String> params = catalog.endpointProperties(recipient.toString());
            for (String k : lenient.keySet()) {
                params.remove(k);
            }
            String answer = catalog.asEndpointUri(scheme, params, false);
            return answer;
        } else {
            // no dynamic
            return null;
        }
    }
}
