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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Body;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.language.XPath;

public class ClaimCheckTest extends ContextTestSupport {

    // in memory data store for testing only!
    public static Map<String, Object> dataStore = new HashMap<String, Object>();
    
    public void testClaimCheck() throws Exception {
        String body = "<order custId=\"123\"><lotsOfContent/></order>";
        
        // check to make sure the message body gets added back in properly
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).body().isEqualTo(body);

        // check to make sure the claim check is added to the message and
        // the body is removed
        MockEndpoint testCheckpointEndpoint = getMockEndpoint("mock:testCheckpoint");
        testCheckpointEndpoint.expectedMessageCount(1);
        testCheckpointEndpoint.expectedHeaderReceived("claimCheck", "123");
        testCheckpointEndpoint.message(0).body().isNull();
        
        template.sendBody("direct:start", body);
                
        assertMockEndpointsSatisfied();        
    }

    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("checkLuggage", new CheckLuggageBean());
        jndi.bind("dataEnricher", new DataEnricherBean());
        return jndi;
    }    

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e1  
                from("direct:start").to("bean:checkLuggage", "mock:testCheckpoint", "bean:dataEnricher", "mock:result");
                // END SNIPPET: e1  
            }
        };
    }

    // START SNIPPET: e2  
    public static final class CheckLuggageBean {        
        public void checkLuggage(Exchange exchange, @Body String body, @XPath("/order/@custId") String custId) {   
            // store the message body into the data store, using the custId as the claim check
            dataStore.put(custId, body);
            // add the claim check as a header
            exchange.getIn().setHeader("claimCheck", custId);
            // remove the body from the message
            exchange.getIn().setBody(null);
        }
    }    
    // END SNIPPET: e2  
    
    // START SNIPPET: e3
    public static final class DataEnricherBean {
        public void addDataBackIn(Exchange exchange, @Header("claimCheck") String claimCheck) { 
            // query the data store using the claim check as the key and add the data
            // back into the message body
            exchange.getIn().setBody(dataStore.get(claimCheck));
            // remove the message data from the data store
            dataStore.remove(claimCheck);
            // remove the claim check header
            exchange.getIn().removeHeader("claimCheck");
        }
    }    
    // END SNIPPET: e3  
}
