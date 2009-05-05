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

import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.cxf.jaxws.context.WrappedMessageContext;

public class CxfRawMessageRouterTest extends CxfSimpleRouterTest {
    private String routerEndpointURI = "cxf://" + ROUTER_ADDRESS + "?" + SERVICE_CLASS + "&dataFormat=MESSAGE";
    private String serviceEndpointURI = "cxf://" + SERVICE_ADDRESS + "?" + SERVICE_CLASS + "&dataFormat=MESSAGE";
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(routerEndpointURI).to("log:org.apache.camel?level=DEBUG").to(serviceEndpointURI).to("mock:result");
            }
        };
    }
    
    public void testTheContentType() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.reset();
        HelloService client = getCXFClient();
        client.echo("hello world");        
        Map context = (Map)result.assertExchangeReceived(0).getIn().getHeaders().get("ResponseContext"); 
        assertNotNull("Expect to get the protocal header ", context.get("org.apache.cxf.message.Message.PROTOCOL_HEADERS"));
        Map protocalHeaders = (Map) context.get("org.apache.cxf.message.Message.PROTOCOL_HEADERS");
        assertEquals("Should get the content type", protocalHeaders.get("content-type").toString(), "[text/xml; charset=utf-8]");
        assertEquals("Should get the response code ", context.get("org.apache.cxf.message.Message.RESPONSE_CODE"), 200);
    }
}
