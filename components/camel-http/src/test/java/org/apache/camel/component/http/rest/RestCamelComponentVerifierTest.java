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
package org.apache.camel.component.http.rest;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.component.http.BaseHttpTest;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.camel.component.rest.RestComponent;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.localserver.RequestBasicAuth;
import org.apache.http.localserver.ResponseBasicUnauthorized;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.ResponseContent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.eclipse.jetty.http.HttpMethod.GET;

public class RestCamelComponentVerifierTest extends BaseHttpTest {

    private HttpServer localServer;
    private Map<String, Object> parameters;
    private ComponentVerifierExtension verifier;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        localServer = ServerBootstrap.bootstrap()
                .setHttpProcessor(getHttpProcessor())
                .registerHandler("/verify", new BasicValidationHandler(GET.name(), null, null, getExpectedContent()))
                .create();

        localServer.start();

        RestComponent component = context().getComponent("rest", RestComponent.class);
        verifier = component.getVerifier();

        parameters = new HashMap<>();
        parameters.put("componentName", "http");
        parameters.put("host", "http://localhost:" + localServer.getLocalPort());
        parameters.put("path", "verify");
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
                Collections.singletonList(
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

    @SuppressWarnings("unused")
    protected String getLocalServerUri(String contextPath) {
        return "http://"
                + localServer.getInetAddress().getHostName()
                + ":"
                + localServer.getLocalPort()
                + (contextPath != null
                ? contextPath.startsWith("/") ? contextPath : "/" + contextPath
                : "");
    }

    // *************************************************
    // Tests
    // *************************************************
    @Test
    public void testParameters() throws Exception {

        parameters.put("method", "get");

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.PARAMETERS, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testMissingRestParameters() throws Exception {
        // This parameter does not belong to the rest component and validation
        // is delegated to the transport component
        parameters.put("copyHeaders", false);

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.PARAMETERS, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        assertEquals(1, result.getErrors().size());
        assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.MISSING_PARAMETER, result.getErrors().get(0).getCode());
        assertEquals(1, result.getErrors().get(0).getParameterKeys().size());
        assertTrue(result.getErrors().get(0).getParameterKeys().contains("method"));
    }

    @Test
    public void testWrongComponentParameters() {

        parameters.put("method", "get");

        // This parameter does not belong to the rest component and validation
        // is delegated to the transport component
        parameters.put("nonExistingOption", true);

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.PARAMETERS, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        assertEquals(1, result.getErrors().size());
        assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.UNKNOWN_PARAMETER, result.getErrors().get(0).getCode());
        assertEquals(1, result.getErrors().get(0).getParameterKeys().size());
        assertTrue(result.getErrors().get(0).getParameterKeys().contains("nonExistingOption"));
    }

    @Test
    public void testConnectivity() throws Exception {

        parameters.put("method", "get");

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }
}
