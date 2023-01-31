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
package org.apache.camel.component.servicenow;

import java.util.Map;

import jakarta.ws.rs.ProcessingException;

import org.apache.camel.Component;
import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "SERVICENOW_INSTANCE", matches = ".*",
                              disabledReason = "Service now instance was not provided")
public class ServiceNowComponentVerifierExtensionIT extends ServiceNowITSupport {
    public ServiceNowComponentVerifierExtensionIT() {
        super(false);
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    protected ComponentVerifierExtension getExtension() {
        Component component = context().getComponent("servicenow");
        ComponentVerifierExtension verifier
                = component.getExtension(ComponentVerifierExtension.class).orElseThrow(IllegalStateException::new);

        return verifier;
    }

    // *********************************
    // Parameters validation
    // *********************************

    @Test
    public void testParameter() {
        Map<String, Object> parameters = getParameters();
        ComponentVerifierExtension.Result result
                = getExtension().verify(ComponentVerifierExtension.Scope.PARAMETERS, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testMissingMandatoryParameter() {
        Map<String, Object> parameters = getParameters();
        parameters.remove("instanceName");
        ComponentVerifierExtension.Result result
                = getExtension().verify(ComponentVerifierExtension.Scope.PARAMETERS, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        assertEquals(1, result.getErrors().size());
        assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.MISSING_PARAMETER,
                result.getErrors().get(0).getCode());
        assertEquals("instanceName", result.getErrors().get(0).getParameterKeys().iterator().next());
    }

    @Test
    public void testMissingMandatoryAuthenticationParameter() {
        Map<String, Object> parameters = getParameters();
        parameters.remove("userName");
        ComponentVerifierExtension.Result result
                = getExtension().verify(ComponentVerifierExtension.Scope.PARAMETERS, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        assertEquals(1, result.getErrors().size());
        assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.MISSING_PARAMETER,
                result.getErrors().get(0).getCode());
        assertEquals("userName", result.getErrors().get(0).getParameterKeys().iterator().next());
    }

    // *********************************
    // Connectivity validation
    // *********************************

    @Test
    public void testConnectivity() {
        Map<String, Object> parameters = getParameters();
        ComponentVerifierExtension.Result result
                = getExtension().verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testConnectivityOnCustomTable() {
        Map<String, Object> parameters = getParameters();
        parameters.put("table", "ticket");

        ComponentVerifierExtension.Result result
                = getExtension().verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testConnectivityWithWrongInstance() {
        Map<String, Object> parameters = getParameters();
        parameters.put("instanceName", "unknown-instance");

        ComponentVerifierExtension.Result result
                = getExtension().verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        assertEquals(1, result.getErrors().size());
        assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.EXCEPTION, result.getErrors().get(0).getCode());
        assertNotNull(result.getErrors().get(0).getDetails()
                .get(ComponentVerifierExtension.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE));
        assertTrue(result.getErrors().get(0).getDetails().get(
                ComponentVerifierExtension.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE) instanceof ProcessingException);
    }

    @Test
    public void testConnectivityWithWrongTable() {
        Map<String, Object> parameters = getParameters();
        parameters.put("table", "unknown");

        ComponentVerifierExtension.Result result
                = getExtension().verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        assertEquals(1, result.getErrors().size());
        assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.EXCEPTION, result.getErrors().get(0).getCode());
        assertNotNull(result.getErrors().get(0).getDetails()
                .get(ComponentVerifierExtension.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE));
        assertEquals(400, result.getErrors().get(0).getDetails()
                .get(ComponentVerifierExtension.VerificationError.HttpAttribute.HTTP_CODE));
        assertTrue(result.getErrors().get(0).getDetails().get(
                ComponentVerifierExtension.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE) instanceof ServiceNowException);
    }

    @Test
    public void testConnectivityWithWrongAuthentication() {
        Map<String, Object> parameters = getParameters();
        parameters.put("userName", "unknown-user");
        parameters.remove("oauthClientId");
        parameters.remove("oauthClientSecret");

        ComponentVerifierExtension.Result result
                = getExtension().verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        assertEquals(1, result.getErrors().size());
        assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.AUTHENTICATION,
                result.getErrors().get(0).getCode());
        assertNotNull(result.getErrors().get(0).getDetails()
                .get(ComponentVerifierExtension.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE));
        assertEquals(401, result.getErrors().get(0).getDetails()
                .get(ComponentVerifierExtension.VerificationError.HttpAttribute.HTTP_CODE));
        assertTrue(result.getErrors().get(0).getDetails().get(
                ComponentVerifierExtension.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE) instanceof ServiceNowException);
        assertTrue(result.getErrors().get(0).getParameterKeys().contains("userName"));
        assertTrue(result.getErrors().get(0).getParameterKeys().contains("password"));
        assertTrue(result.getErrors().get(0).getParameterKeys().contains("oauthClientId"));
        assertTrue(result.getErrors().get(0).getParameterKeys().contains("oauthClientSecret"));
    }
}
