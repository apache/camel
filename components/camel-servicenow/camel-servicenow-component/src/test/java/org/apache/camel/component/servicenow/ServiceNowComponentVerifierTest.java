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
package org.apache.camel.component.servicenow;

import java.util.Map;
import javax.ws.rs.ProcessingException;

import org.apache.camel.ComponentVerifier;
import org.junit.Assert;
import org.junit.Test;

public class ServiceNowComponentVerifierTest extends ServiceNowTestSupport {
    public ServiceNowComponentVerifierTest() {
        super(false);
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    protected ComponentVerifier getVerifier() {
        return context().getComponent("servicenow", ServiceNowComponent.class).getVerifier();
    }

    // *********************************
    // Parameters validation
    // *********************************

    @Test
    public void testParameter() {
        Map<String, Object> parameters = getParameters();
        ComponentVerifier.Result result = getVerifier().verify(ComponentVerifier.Scope.PARAMETERS, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testMissingMandatoryParameter() {
        Map<String, Object> parameters = getParameters();
        parameters.remove("instanceName");
        ComponentVerifier.Result result = getVerifier().verify(ComponentVerifier.Scope.PARAMETERS, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(1, result.getErrors().size());
        Assert.assertEquals(ComponentVerifier.VerificationError.StandardCode.MISSING_PARAMETER, result.getErrors().get(0).getCode());
        Assert.assertEquals("instanceName", result.getErrors().get(0).getParameterKeys().iterator().next());
    }

    @Test
    public void testMissingMandatoryAuthenticationParameter() {
        Map<String, Object> parameters = getParameters();
        parameters.remove("userName");
        ComponentVerifier.Result result = getVerifier().verify(ComponentVerifier.Scope.PARAMETERS, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(1, result.getErrors().size());
        Assert.assertEquals(ComponentVerifier.VerificationError.StandardCode.MISSING_PARAMETER, result.getErrors().get(0).getCode());
        Assert.assertEquals("userName", result.getErrors().get(0).getParameterKeys().iterator().next());
    }

    // *********************************
    // Connectivity validation
    // *********************************

    @Test
    public void testConnectivity() {
        Map<String, Object> parameters = getParameters();
        ComponentVerifier.Result result = getVerifier().verify(ComponentVerifier.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testConnectivityOnCustomTable() {
        Map<String, Object> parameters = getParameters();
        parameters.put("table", "ticket");

        ComponentVerifier.Result result = getVerifier().verify(ComponentVerifier.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testConnectivityWithWrongInstance() {
        Map<String, Object> parameters = getParameters();
        parameters.put("instanceName", "unknown-instance");

        ComponentVerifier.Result result = getVerifier().verify(ComponentVerifier.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(1, result.getErrors().size());
        Assert.assertEquals(ComponentVerifier.VerificationError.StandardCode.EXCEPTION, result.getErrors().get(0).getCode());
        Assert.assertNotNull(result.getErrors().get(0).getDetails().get(ComponentVerifier.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE));
        Assert.assertTrue(result.getErrors().get(0).getDetails().get(ComponentVerifier.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE) instanceof ProcessingException);
    }

    @Test
    public void testConnectivityWithWrongTable() {
        Map<String, Object> parameters = getParameters();
        parameters.put("table", "unknown");

        ComponentVerifier.Result result = getVerifier().verify(ComponentVerifier.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(1, result.getErrors().size());
        Assert.assertEquals(ComponentVerifier.VerificationError.StandardCode.EXCEPTION, result.getErrors().get(0).getCode());
        Assert.assertNotNull(result.getErrors().get(0).getDetails().get(ComponentVerifier.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE));
        Assert.assertEquals(400, result.getErrors().get(0).getDetails().get(ComponentVerifier.VerificationError.HttpAttribute.HTTP_CODE));
        Assert.assertTrue(result.getErrors().get(0).getDetails().get(ComponentVerifier.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE) instanceof ServiceNowException);
    }

    @Test
    public void testConnectivityWithWrongAuthentication() {
        Map<String, Object> parameters = getParameters();
        parameters.put("userName", "unknown-user");
        parameters.remove("oauthClientId");
        parameters.remove("oauthClientSecret");

        ComponentVerifier.Result result = getVerifier().verify(ComponentVerifier.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(1, result.getErrors().size());
        Assert.assertEquals(ComponentVerifier.VerificationError.StandardCode.AUTHENTICATION, result.getErrors().get(0).getCode());
        Assert.assertNotNull(result.getErrors().get(0).getDetails().get(ComponentVerifier.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE));
        Assert.assertEquals(401, result.getErrors().get(0).getDetails().get(ComponentVerifier.VerificationError.HttpAttribute.HTTP_CODE));
        Assert.assertTrue(result.getErrors().get(0).getDetails().get(ComponentVerifier.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE) instanceof ServiceNowException);
        Assert.assertTrue(result.getErrors().get(0).getParameterKeys().contains("userName"));
        Assert.assertTrue(result.getErrors().get(0).getParameterKeys().contains("password"));
        Assert.assertTrue(result.getErrors().get(0).getParameterKeys().contains("oauthClientId"));
        Assert.assertTrue(result.getErrors().get(0).getParameterKeys().contains("oauthClientSecret"));
    }
}
