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
package org.apache.camel.component.aws2.lambda;

import org.apache.camel.PropertyBindingException;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.lambda.LambdaClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LambdaComponentClientRegistryTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalKMSClientConfiguration() throws Exception {

        LambdaClient awsLambdaClient = new AmazonLambdaClientMock();
        context.getRegistry().bind("awsLambdaClient", awsLambdaClient);
        Lambda2Component component = context.getComponent("aws2-lambda", Lambda2Component.class);
        Lambda2Endpoint endpoint = (Lambda2Endpoint)component
            .createEndpoint("aws2-lambda://myFunction?operation=getFunction&awsLambdaClient=#awsLambdaClient&accessKey=xxx&secretKey=yyy");

        assertNotNull(endpoint.getConfiguration().getAwsLambdaClient());
    }

    @Test
    public void createEndpointWithMinimalKMSClientMisconfiguration() throws Exception {

        Lambda2Component component = context.getComponent("aws2-lambda", Lambda2Component.class);
        assertThrows(PropertyBindingException.class, () -> {
            component
                .createEndpoint("aws2-lambda://myFunction?operation=getFunction&awsLambdaClient=#awsLambdaClient&accessKey=xxx&secretKey=yyy");
        });
    }
}
