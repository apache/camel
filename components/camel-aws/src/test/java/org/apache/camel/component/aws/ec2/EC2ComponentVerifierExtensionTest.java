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
package org.apache.camel.component.aws.ec2;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Component;
import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;

public class EC2ComponentVerifierExtensionTest extends CamelTestSupport {

    // *************************************************
    // Tests (parameters)
    // *************************************************
    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testParameters() throws Exception {
        Component component = context().getComponent("aws-ec2");

        ComponentVerifierExtension verifier = component.getExtension(ComponentVerifierExtension.class).orElseThrow(IllegalStateException::new);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("secretKey", "l");
        parameters.put("accessKey", "k");
        parameters.put("region", "l");
        parameters.put("label", "test");
        parameters.put("operation", EC2Operations.describeInstances.toString());

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.PARAMETERS, parameters);

        Assert.assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testConnectivity() throws Exception {
        Component component = context().getComponent("aws-ec2");
        ComponentVerifierExtension verifier = component.getExtension(ComponentVerifierExtension.class).orElseThrow(IllegalStateException::new);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("secretKey", "l");
        parameters.put("accessKey", "k");
        parameters.put("region", "us-east-1");
        parameters.put("label", "test");
        parameters.put("operation", EC2Operations.describeInstances.toString());

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
    }

}
