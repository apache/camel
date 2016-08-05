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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;

public class DetourTest extends ContextTestSupport {
    
    private static final String BODY = "<order custId=\"123\"/>";
    private ControlBean controlBean;

    public void testDetourSet() throws Exception {
        controlBean.setDetour(true);
        
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).body().isEqualTo(BODY);

        MockEndpoint detourEndpoint = getMockEndpoint("mock:detour");
        detourEndpoint.expectedMessageCount(1);
        detourEndpoint.message(0).body().isEqualTo(BODY);
        
        template.sendBody("direct:start", BODY);
                
        assertMockEndpointsSatisfied();        
    }

    public void testDetourNotSet() throws Exception {      
        controlBean.setDetour(false);
        
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).body().isEqualTo(BODY);

        MockEndpoint detourEndpoint = getMockEndpoint("mock:detour");
        detourEndpoint.expectedMessageCount(0);
        
        template.sendBody("direct:start", BODY);
                
        assertMockEndpointsSatisfied();        
    }    
    
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        controlBean = new ControlBean();
        jndi.bind("controlBean", controlBean);
        return jndi;
    }    

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e1  
                from("direct:start").choice()
                    .when().method("controlBean", "isDetour").to("mock:detour").end()
                    .to("mock:result");                
                // END SNIPPET: e1  
            }
        };
    }
    
    public final class ControlBean {
        private boolean detour;  

        public void setDetour(boolean detour) {
            this.detour = detour;
        }

        public boolean isDetour() {
            return detour;
        }
    }    
}
