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
package org.apache.camel.component.aws.lambda;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambdaClient;

import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import static org.mockito.Mockito.mock;


public class LambdaComponentConfigurationTest extends CamelTestSupport {
    AWSLambdaClient awsLambdaClient = mock(AWSLambdaClient.class);

    @Test
    public void createEndpointWithMinimalConfiguration() throws Exception {
        LambdaComponent component = new LambdaComponent(context);
        LambdaEndpoint endpoint = (LambdaEndpoint) component.createEndpoint(
            "aws-lambda://myFunction?operation=getFunction&awsLambdaClient=#awsLambdaClient&accessKey=xxx&secretKey=yyy");

        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNotNull(endpoint.getConfiguration().getAwsLambdaClient());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutOperation() throws Exception {
        LambdaComponent component = new LambdaComponent(context);
        component.createEndpoint("aws-lambda://myFunction");
    }

    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutAmazonLambdaClientConfiguration() throws Exception {
        LambdaComponent component = new LambdaComponent(context);
        component.createEndpoint("aws-lambda://myFunction?operation=getFunction");
    }

    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutAccessKeyConfiguration() throws Exception {
        LambdaComponent component = new LambdaComponent(context);
        component.createEndpoint("aws-lambda://myFunction?operation=getFunction&secretKey=yyy");
    }

    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutSecretKeyConfiguration() throws Exception {
        LambdaComponent component = new LambdaComponent(context);
        component.createEndpoint("aws-lambda://myFunction?operation=getFunction&accessKey=xxx");
    }

    @Test
    public void createEndpointWithoutSecretKeyAndAccessKeyConfiguration() throws Exception {
        LambdaComponent component = new LambdaComponent(context);
        component.createEndpoint("aws-lambda://myFunction?operation=getFunction&awsLambdaClient=#awsLambdaClient");
    }
    
    @Test
    public void createEndpointWithComponentElements() throws Exception {
        LambdaComponent component = new LambdaComponent(context);
        component.setAccessKey("XXX");
        component.setSecretKey("YYY");
        LambdaEndpoint endpoint = (LambdaEndpoint)component.createEndpoint("aws-lambda://myFunction");
        
        assertEquals("myFunction", endpoint.getConfiguration().getFunction());
        assertEquals("XXX", endpoint.getConfiguration().getAccessKey());
        assertEquals("YYY", endpoint.getConfiguration().getSecretKey());
    }
    
    @Test
    public void createEndpointWithComponentAndEndpointElements() throws Exception {
        LambdaComponent component = new LambdaComponent(context);
        component.setAccessKey("XXX");
        component.setSecretKey("YYY");
        component.setRegion(Regions.US_WEST_1.toString());
        LambdaEndpoint endpoint = (LambdaEndpoint)component.createEndpoint("aws-lambda://myFunction?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1");
        
        assertEquals("myFunction", endpoint.getConfiguration().getFunction());
        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("awsLambdaClient", awsLambdaClient);
        return registry;
    }
}