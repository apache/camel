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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.spi.RestProducerFactory;
import org.junit.Assert;
import org.junit.Test;

public class RestEndpointTest {

    public static class MockRest extends DefaultComponent implements RestProducerFactory {
        @Override
        public Producer createProducer(CamelContext camelContext, String host, String verb, String basePath,
            String uriTemplate, String queryParameters, String consumes, String produces,
            Map<String, Object> parameters) throws Exception {
            return null;
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
            throws Exception {
            return null;
        }
    }

    @Test
    public void shouldConfigureBindingMode() throws Exception {
        final CamelContext context = new DefaultCamelContext();
        context.addComponent("mock-rest", new MockRest());

        final RestComponent restComponent = new RestComponent();
        restComponent.setCamelContext(context);

        final RestEndpoint restEndpoint = new RestEndpoint("rest:GET:/path", restComponent);
        restEndpoint.setComponentName("mock-rest");
        restEndpoint.setParameters(new HashMap<>());

        restEndpoint.setBindingMode(RestBindingMode.json);

        final RestProducer producer = (RestProducer) restEndpoint.createProducer();

        Assert.assertEquals(producer.getBindingMode(), RestBindingMode.json);
    }
}
