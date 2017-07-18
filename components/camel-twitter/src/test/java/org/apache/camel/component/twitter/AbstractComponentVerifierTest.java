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
package org.apache.camel.component.twitter;

import java.util.Map;

import org.apache.camel.ComponentVerifier;
import org.junit.Assert;
import org.junit.Test;


public abstract class AbstractComponentVerifierTest extends CamelTwitterTestSupport {

    protected abstract String getComponentScheme();
    
    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testConnectivity() {
        AbstractTwitterComponent component = context().getComponent(getComponentScheme(), AbstractTwitterComponent.class);
        ComponentVerifier verifier = component.getVerifier();

        Map<String, Object> parameters = getParameters();
        ComponentVerifier.Result result = verifier.verify(ComponentVerifier.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testInvalidKeyConfiguration() {
        AbstractTwitterComponent component = context().getComponent(getComponentScheme(), AbstractTwitterComponent.class);
        ComponentVerifier verifier = component.getVerifier();

        Map<String, Object> parameters = getParameters();
        parameters.put("consumerKey", "invalid");

        ComponentVerifier.Result result = verifier.verify(ComponentVerifier.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(1, result.getErrors().size());
        Assert.assertEquals(ComponentVerifier.VerificationError.StandardCode.AUTHENTICATION, result.getErrors().get(0).getCode());
        Assert.assertEquals(401, result.getErrors().get(0).getDetails().get(ComponentVerifier.VerificationError.asAttribute("twitter_status_code")));
        Assert.assertEquals(32, result.getErrors().get(0).getDetails().get(ComponentVerifier.VerificationError.asAttribute("twitter_error_code")));
    }

    @Test
    public void testInvalidTokenConfiguration() {
        AbstractTwitterComponent component = context().getComponent(getComponentScheme(), AbstractTwitterComponent.class);
        ComponentVerifier verifier = component.getVerifier();

        Map<String, Object> parameters = getParameters();
        parameters.put("accessToken", "invalid");

        ComponentVerifier.Result result = verifier.verify(ComponentVerifier.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(1, result.getErrors().size());
        Assert.assertEquals(ComponentVerifier.VerificationError.StandardCode.AUTHENTICATION, result.getErrors().get(0).getCode());
        Assert.assertEquals(401, result.getErrors().get(0).getDetails().get(ComponentVerifier.VerificationError.asAttribute("twitter_status_code")));
        Assert.assertEquals(89, result.getErrors().get(0).getDetails().get(ComponentVerifier.VerificationError.asAttribute("twitter_error_code")));
        Assert.assertEquals(1, result.getErrors().get(0).getParameterKeys().size());
        Assert.assertEquals("accessToken", result.getErrors().get(0).getParameterKeys().iterator().next());
    }
}
