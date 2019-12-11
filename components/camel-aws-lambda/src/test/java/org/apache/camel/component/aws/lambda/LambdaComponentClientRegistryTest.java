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
package org.apache.camel.component.aws.lambda;

import com.amazonaws.services.lambda.AWSLambdaClient;
import org.apache.camel.PropertyBindingException;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.mockito.Mockito.mock;

public class LambdaComponentClientRegistryTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalKMSClientConfiguration() throws Exception {

        AWSLambdaClient awsLambdaClient = mock(AWSLambdaClient.class);
        context.getRegistry().bind("awsLambdaClient", awsLambdaClient);
        LambdaComponent component = context.getComponent("aws-lambda", LambdaComponent.class);
        LambdaEndpoint endpoint = (LambdaEndpoint) component.createEndpoint(
                "aws-lambda://myFunction?operation=getFunction&awsLambdaClient=#awsLambdaClient&accessKey=xxx&secretKey=yyy");

        assertNotNull(endpoint.getConfiguration().getAwsLambdaClient());
    }
    
    @Test(expected = PropertyBindingException.class)
    public void createEndpointWithMinimalKMSClientMisconfiguration() throws Exception {

        LambdaComponent component = context.getComponent("aws-lambda", LambdaComponent.class);
        LambdaEndpoint endpoint = (LambdaEndpoint) component.createEndpoint(
                "aws-lambda://myFunction?operation=getFunction&awsLambdaClient=#awsLambdaClient&accessKey=xxx&secretKey=yyy");
    }
}
