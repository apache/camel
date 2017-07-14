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
package org.apache.camel.component.http4.rest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ComponentVerifier;
import org.apache.camel.component.http4.BaseHttpTest;
import org.apache.camel.component.http4.handler.BasicValidationHandler;
import org.apache.camel.component.rest.RestComponent;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.localserver.RequestBasicAuth;
import org.apache.http.localserver.ResponseBasicUnauthorized;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.ResponseContent;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class RestCamelComponentVerifierTest extends BaseHttpTest {
    private HttpServer localServer;

    @Before
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap()
            .setHttpProcessor(getHttpProcessor())
            .registerHandler("/verify", new BasicValidationHandler("GET", null, null, getExpectedContent()))
            .create();

        localServer.start();

        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (localServer != null) {
            localServer.stop();
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private HttpProcessor getHttpProcessor() {
        return new ImmutableHttpProcessor(
            Arrays.asList(
                new RequestBasicAuth()
            ),
            Arrays.asList(
                new ResponseContent(),
                new ResponseBasicUnauthorized())
        );
    }

    // *************************************************
    // Helpers
    // *************************************************

    protected String getLocalServerUri(String contextPath) {
        return new StringBuilder()
            .append("http://")
            .append(localServer.getInetAddress().getHostName())
            .append(":")
            .append(localServer.getLocalPort())
            .append(contextPath != null
                ? contextPath.startsWith("/") ? contextPath : "/" + contextPath
                : "")
            .toString();
    }

    // *************************************************
    // Tests
    // *************************************************
    @Test
    public void testParameters() throws Exception {
        RestComponent component = context().getComponent("rest", RestComponent.class);
        ComponentVerifier verifier = component.getVerifier();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("componentName", "http4");
        parameters.put("host", "http://localhost:" + localServer.getLocalPort());
        parameters.put("path", "verify");
        parameters.put("method", "get");

        ComponentVerifier.Result result = verifier.verify(ComponentVerifier.Scope.PARAMETERS, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testMissingRestParameters() throws Exception {
        RestComponent component = context.getComponent("rest", RestComponent.class);
        ComponentVerifier verifier = component.getVerifier();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("componentName", "http4");
        parameters.put("host", "http://localhost:" + localServer.getLocalPort());
        parameters.put("path", "verify");

        // This parameter does not belong to the rest component and validation
        // is delegated to the transport component
        parameters.put("copyHeaders", false);

        ComponentVerifier.Result result = verifier.verify(ComponentVerifier.Scope.PARAMETERS, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(1, result.getErrors().size());
        Assert.assertEquals(ComponentVerifier.VerificationError.StandardCode.MISSING_PARAMETER, result.getErrors().get(0).getCode());
        Assert.assertEquals(1, result.getErrors().get(0).getParameterKeys().size());
        Assert.assertTrue(result.getErrors().get(0).getParameterKeys().contains("method"));
    }

    @Test
    public void testWrongComponentParameters() throws Exception {
        RestComponent component = context.getComponent("rest", RestComponent.class);
        ComponentVerifier verifier = component.getVerifier();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("componentName", "http4");
        parameters.put("host", "http://localhost:" + localServer.getLocalPort());
        parameters.put("path", "verify");
        parameters.put("method", "get");

        // This parameter does not belong to the rest component and validation
        // is delegated to the transport component
        parameters.put("nonExistingOption", true);

        ComponentVerifier.Result result = verifier.verify(ComponentVerifier.Scope.PARAMETERS, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(1, result.getErrors().size());
        Assert.assertEquals(ComponentVerifier.VerificationError.StandardCode.UNKNOWN_PARAMETER, result.getErrors().get(0).getCode());
        Assert.assertEquals(1, result.getErrors().get(0).getParameterKeys().size());
        Assert.assertTrue(result.getErrors().get(0).getParameterKeys().contains("nonExistingOption"));
    }

    @Test
    public void testConnectivity() throws Exception {
        RestComponent component = context().getComponent("rest", RestComponent.class);
        ComponentVerifier verifier = component.getVerifier();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("componentName", "http4");
        parameters.put("host", "http://localhost:" + localServer.getLocalPort());
        parameters.put("path", "verify");
        parameters.put("method", "get");

        ComponentVerifier.Result result = verifier.verify(ComponentVerifier.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.OK, result.getStatus());
    }
}