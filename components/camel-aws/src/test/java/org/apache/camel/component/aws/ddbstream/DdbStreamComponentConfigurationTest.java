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
package org.apache.camel.component.aws.ddbstream;

import com.amazonaws.regions.Regions;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class DdbStreamComponentConfigurationTest extends CamelTestSupport {
    
    @Test
    public void createEndpointWithAccessAndSecretKey() throws Exception {
        DdbStreamComponent component = new DdbStreamComponent(context);
        DdbStreamEndpoint endpoint = (DdbStreamEndpoint)component.createEndpoint("aws-ddb://myTable?accessKey=xxxxx&secretKey=yyyyy");
        
        assertEquals("myTable", endpoint.getConfiguration().getTableName());
        assertEquals("xxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());    
    }
    
    @Test
    public void createEndpointWithComponentElements() throws Exception {
        DdbStreamComponent component = new DdbStreamComponent(context);
        component.setAccessKey("XXX");
        component.setSecretKey("YYY");
        DdbStreamEndpoint endpoint = (DdbStreamEndpoint)component.createEndpoint("aws-ddb://myTable");
        
        assertEquals("myTable", endpoint.getConfiguration().getTableName());
        assertEquals("XXX", endpoint.getConfiguration().getAccessKey());
        assertEquals("YYY", endpoint.getConfiguration().getSecretKey());
    }
    
    @Test
    public void createEndpointWithComponentAndEndpointElements() throws Exception {
        DdbStreamComponent component = new DdbStreamComponent(context);
        component.setAccessKey("XXX");
        component.setSecretKey("YYY");
        component.setRegion(Regions.US_WEST_1.toString());
        DdbStreamEndpoint endpoint = (DdbStreamEndpoint)component.createEndpoint("aws-ddb://myTable?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1");
        
        assertEquals("myTable", endpoint.getConfiguration().getTableName());
        assertEquals("xxxxxx", endpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", endpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", endpoint.getConfiguration().getRegion());
    }
    
}