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
package org.apache.camel.component.undertow.rest;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.component.rest.RestComponent;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.junit.Assert;
import org.junit.Test;

public class RestUndertowComponentVerifierTest extends BaseUndertowTest {
    @Test
    public void testParameters() throws Exception {
        RestComponent component = context().getComponent("rest", RestComponent.class);
        ComponentVerifierExtension verifier = component.getVerifier();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("componentName", "undertow");
        parameters.put("host", "http://localhost:" + getPort());
        parameters.put("path", "verify");
        parameters.put("method", "GET");

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.PARAMETERS, parameters);

        Assert.assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testMissingRestParameters() throws Exception {
        RestComponent component = context.getComponent("rest", RestComponent.class);
        ComponentVerifierExtension verifier = component.getVerifier();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("componentName", "undertow");
        parameters.put("host", "http://localhost:" + getPort());
        parameters.put("path", "verify");

        // This parameter does not belong to the rest component and validation
        // is delegated to the transport component
        parameters.put("tcpNoDelay", true);

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.PARAMETERS, parameters);

        Assert.assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(1, result.getErrors().size());
        Assert.assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.MISSING_PARAMETER, result.getErrors().get(0).getCode());
        Assert.assertEquals(1, result.getErrors().get(0).getParameterKeys().size());
        Assert.assertTrue(result.getErrors().get(0).getParameterKeys().contains("method"));
    }

    @Test
    public void testWrongComponentParameters() throws Exception {
        RestComponent component = context.getComponent("rest", RestComponent.class);
        ComponentVerifierExtension verifier = component.getVerifier();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("componentName", "undertow");
        parameters.put("host", "http://localhost:" + getPort());
        parameters.put("path", "verify");
        parameters.put("method", "GET");

        // This parameter does not belong to the rest component and validation
        // is delegated to the transport component
        parameters.put("nonExistingOption", true);

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.PARAMETERS, parameters);

        Assert.assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(1, result.getErrors().size());
        Assert.assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.UNKNOWN_PARAMETER, result.getErrors().get(0).getCode());
        Assert.assertEquals(1, result.getErrors().get(0).getParameterKeys().size());
        Assert.assertTrue(result.getErrors().get(0).getParameterKeys().contains("nonExistingOption"));
    }

    @Test
    public void testConnectivity() throws Exception {
        RestComponent component = context().getComponent("rest", RestComponent.class);
        ComponentVerifierExtension verifier = component.getVerifier();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("componentName", "undertow");
        parameters.put("host", "http://localhost:" + getPort());
        parameters.put("path", "verify");
        parameters.put("method", "GET");

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration()
                    .component("undertow")
                    .host("localhost")
                    .port(getPort());

                rest("/")
                    .get("/verify")
                    .route()
                        .process(e -> e.getMessage().setBody("ok"));
            }
        };
    }
}
