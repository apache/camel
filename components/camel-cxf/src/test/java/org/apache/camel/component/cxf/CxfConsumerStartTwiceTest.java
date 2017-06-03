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
package org.apache.camel.component.cxf;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;

public class CxfConsumerStartTwiceTest extends Assert {
    static final int PORT = CXFTestSupport.getPort6(); 
    
    
    @Test
    public void startServiceTwice() throws Exception {
        CamelContext context = new DefaultCamelContext();
        
        final String fromStr = "cxf:http://localhost:" + PORT + "/" 
            + this.getClass().getSimpleName() 
            + "/test?serviceClass=org.apache.camel.component.cxf.HelloService";
        
        //add the same route twice...
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from(fromStr)
                    .to("log:POJO");
            }
        });

       
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from(fromStr)
                    .to("log:POJO");
            }
        });            
        
        try {
            context.start();
            fail("Expect to catch an exception here");
        } catch (Exception ex) {
            assertTrue(ex.getMessage().endsWith(
                "Multiple consumers for the same endpoint is not allowed: cxf://http://localhost:" + PORT
                + "/" + getClass().getSimpleName() + "/test?serviceClass=org.apache.camel.component.cxf.HelloService"));
        }
                
        context.stop();
    }

}
