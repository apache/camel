/*
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
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorHelper;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.support.DefaultComponent;
import org.junit.Assert;
import org.junit.Test;

public class RestComponentVerifierExtensionTest extends ContextTestSupport {
    @Override
    protected Registry createRegistry() throws Exception {
        Registry registry = super.createRegistry();
        registry.bind("rest", new RestComponent());
        registry.bind("rest-component", new MyComponent());
        return registry;
    }

    @Test
    public void testParameters() throws Exception {
        RestComponent component = context.getComponent("rest", RestComponent.class);
        RestComponentVerifierExtension verifier = component.getExtension(RestComponentVerifierExtension.class).orElseThrow(() -> new IllegalStateException());

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("componentName", "rest-component");
        parameters.put("host", "http://localhost:1234");
        parameters.put("path", "verify");
        parameters.put("method", "get");

        // This parameter does not belong to the rest component and validation
        // is delegated to the underlying component
        parameters.put("authProxy", "http://localhost:8080");

        RestComponentVerifierExtension.Result result = verifier.verify(RestComponentVerifierExtension.Scope.PARAMETERS, parameters);

        Assert.assertEquals(RestComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testMissingParameters() throws Exception {
        RestComponent component = context.getComponent("rest", RestComponent.class);
        RestComponentVerifierExtension verifier = component.getExtension(RestComponentVerifierExtension.class).orElseThrow(() -> new IllegalStateException());

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("componentName", "rest-component");
        parameters.put("host", "http://localhost:" + 1234);
        parameters.put("path", "verify");

        // This parameter does not belong to the rest component and validation
        // is delegated to the underlying component
        parameters.put("authProxy", "http://localhost:8080");

        RestComponentVerifierExtension.Result result = verifier.verify(RestComponentVerifierExtension.Scope.PARAMETERS, parameters);

        Assert.assertEquals(RestComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(1, result.getErrors().size());
        Assert.assertEquals(RestComponentVerifierExtension.VerificationError.StandardCode.MISSING_PARAMETER, result.getErrors().get(0).getCode());
        Assert.assertEquals(1, result.getErrors().get(0).getParameterKeys().size());
        Assert.assertTrue(result.getErrors().get(0).getParameterKeys().contains("method"));
    }

    // ***************************************************
    //
    // ***************************************************

    private final class MyComponent extends DefaultComponent implements RestProducerFactory, RestConsumerFactory {

        public MyComponent() {
            registerExtension(new ComponentVerifierExtension() {
                @Override
                public Result verify(Scope scope, Map<String, Object> parameters) {
                    return ResultBuilder.withStatusAndScope(RestComponentVerifierExtension.Result.Status.OK, scope).error(ResultErrorHelper.requiresOption("authProxy", parameters))
                        .build();
                }
            });
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            throw new UnsupportedOperationException();
        }

        @Override
        public Producer createProducer(CamelContext camelContext, String host, String verb, String basePath, String uriTemplate, String queryParameters, String consumes,
                                       String produces, RestConfiguration configuration, Map<String, Object> parameters)
            throws Exception {
            throw new UnsupportedOperationException();
        }

        @Override
        public Consumer createConsumer(CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate, String consumes, String produces,
                                       RestConfiguration configuration, Map<String, Object> parameters)
            throws Exception {
            throw new UnsupportedOperationException();
        }
    }
}
