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
package org.apache.camel.component.undertow;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UndertowComponentVerifierTest extends BaseUndertowTest {
    @Test
    public void testParameters() throws Exception {
        UndertowComponent component = context().getComponent("undertow", UndertowComponent.class);
        ComponentVerifierExtension verifier = component.getVerifier();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("httpURI", "http://localhost:" + getPort());
        parameters.put("tcpNoDelay", "true");

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.PARAMETERS, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testMissingParameters() throws Exception {
        UndertowComponent component = context.getComponent("undertow", UndertowComponent.class);
        ComponentVerifierExtension verifier = component.getVerifier();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("tcpNoDelay", "true");

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.PARAMETERS, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        assertEquals(1, result.getErrors().size());
        assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.MISSING_PARAMETER,
                result.getErrors().get(0).getCode());
        assertEquals(1, result.getErrors().get(0).getParameterKeys().size());
        assertTrue(result.getErrors().get(0).getParameterKeys().contains("httpURI"));
    }

    @Test
    public void testConnectivity() throws Exception {
        UndertowComponent component = context().getComponent("undertow", UndertowComponent.class);
        ComponentVerifierExtension verifier = component.getVerifier();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("httpURI", "http://localhost:" + getPort());
        parameters.put("tcpNoDelay", "true");

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testConnectivityError() throws Exception {
        UndertowComponent component = context().getComponent("undertow", UndertowComponent.class);
        ComponentVerifierExtension verifier = component.getVerifier();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("httpURI", "http://no.host:" + getPort());

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        assertEquals(1, result.getErrors().size());

        ComponentVerifierExtension.VerificationError error = result.getErrors().get(0);

        assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.EXCEPTION, error.getCode());
        assertTrue(error.getDetail(
                ComponentVerifierExtension.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE) instanceof UnknownHostException);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("undertow:http://localhost:{{port}}")
                        .process(e -> e.getMessage().setBody("ok"));
            }
        };
    }
}
