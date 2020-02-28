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
package org.apache.camel.component.aws.eks;

import com.amazonaws.Protocol;
import com.amazonaws.regions.Regions;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class EKSComponentConfigurationTest extends CamelTestSupport {

    
    @Test
    public void createEndpointWithComponentElements() throws Exception {
        EKSComponent component = context.getComponent("aws-eks", EKSComponent.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        EKSEndpoint endpoint = (EKSEndpoint)component.createEndpoint("aws-eks://label");
        
        assertEquals("XXX", endpoint.getConfiguration().getAccessKey());
        assertEquals("YYY", endpoint.getConfiguration().getSecretKey());
    }
    
    @Test
    public void createEndpointWithComponentAndEndpointElements() throws Exception {
        EKSComponent component = context.getComponent("aws-eks", EKSComponent.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Regions.US_WEST_1.toString());
        EKSEndpoint endpoint = (EKSEndpoint)component.createEndpoint("aws-eks://label?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1");
        
        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
    }
    
    @Test
    public void createEndpointWithComponentEndpointElementsAndProxy() throws Exception {
        EKSComponent component = context.getComponent("aws-eks", EKSComponent.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Regions.US_WEST_1.toString());
        EKSEndpoint endpoint = (EKSEndpoint)component.createEndpoint("aws-eks://label?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1&proxyHost=localhost&proxyPort=9000&proxyProtocol=HTTP");
        
        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
        assertEquals(Protocol.HTTP, endpoint.getConfiguration().getProxyProtocol());
        assertEquals("localhost", endpoint.getConfiguration().getProxyHost());
        assertEquals(Integer.valueOf(9000), endpoint.getConfiguration().getProxyPort());
    }
}
