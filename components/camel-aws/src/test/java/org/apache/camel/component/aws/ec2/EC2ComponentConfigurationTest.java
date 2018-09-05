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

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2Client;

import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.mockito.Mockito.mock;

public class EC2ComponentConfigurationTest extends CamelTestSupport {
    AmazonEC2Client amazonEc2Client = mock(AmazonEC2Client.class);

    @Test
    public void createEndpointWithMinimalConfiguration() throws Exception {
        EC2Component component = new EC2Component(context);
        EC2Endpoint endpoint = (EC2Endpoint)component.createEndpoint("aws-ec2://TestDomain?amazonEc2Client=#amazonEc2Client&accessKey=xxx&secretKey=yyy");
        
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNotNull(endpoint.getConfiguration().getAmazonEc2Client());
    }
    
    @Test
    public void createEndpointWithOnlyAccessKeyAndSecretKey() throws Exception {
        EC2Component component = new EC2Component(context);
        EC2Endpoint endpoint = (EC2Endpoint)component.createEndpoint("aws-ec2://TestDomain?accessKey=xxx&secretKey=yyy");
        
        assertEquals("xxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyy", endpoint.getConfiguration().getSecretKey());
        assertNull(endpoint.getConfiguration().getAmazonEc2Client());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutDomainName() throws Exception {
        EC2Component component = new EC2Component(context);
        component.createEndpoint("aws-ec2:// ");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutAmazonSDBClientConfiguration() throws Exception {
        EC2Component component = new EC2Component(context);
        component.createEndpoint("aws-ec2://TestDomain");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutAccessKeyConfiguration() throws Exception {
        EC2Component component = new EC2Component(context);
        component.createEndpoint("aws-ec2://TestDomain?secretKey=yyy");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithoutSecretKeyConfiguration() throws Exception {
        EC2Component component = new EC2Component(context);
        component.createEndpoint("aws-ec2://TestDomain?accessKey=xxx");
    }
    
    @Test
    public void createEndpointWithoutSecretKeyAndAccessKeyConfiguration() throws Exception {
        EC2Component component = new EC2Component(context);
        component.createEndpoint("aws-ec2://TestDomain?amazonEc2Client=#amazonEc2Client");
    }
    
    @Test
    public void createEndpointWithComponentElements() throws Exception {
        EC2Component component = new EC2Component(context);
        component.setAccessKey("XXX");
        component.setSecretKey("YYY");
        EC2Endpoint endpoint = (EC2Endpoint)component.createEndpoint("aws-ec2://testDomain");
        
        assertEquals("XXX", endpoint.getConfiguration().getAccessKey());
        assertEquals("YYY", endpoint.getConfiguration().getSecretKey());
    }
    
    @Test
    public void createEndpointWithComponentAndEndpointElements() throws Exception {
        EC2Component component = new EC2Component(context);
        component.setAccessKey("XXX");
        component.setSecretKey("YYY");
        component.setRegion(Regions.US_WEST_1.toString());
        EC2Endpoint endpoint = (EC2Endpoint)component.createEndpoint("aws-s3://testDomain?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1");
        
        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
    }
    
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("amazonEc2Client", amazonEc2Client);
        return registry;
    }
}
