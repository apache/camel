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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.ws.BindingProvider;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.message.Message;
import org.junit.Test;

// We use context to change the producer's endpoint address here
public class CxfProducerContextTest extends CxfProducerTest {

    // *** This class extends CxfProducerTest, so see that class for other tests
    // run by this code

    private static final String TEST_KEY = "sendSimpleMessage-test";
    private static final String TEST_VALUE = "exchange property value should get passed through request context";

    @Test
    public void testExchangePropertyPropagation() throws Exception {
        Exchange exchange = sendSimpleMessage();

        // No direct access to native CXF Message but we can verify the 
        // request context from the Camel exchange
        assertNotNull(exchange);
        Map<String, Object> requestContext = CastUtils.cast((Map<?, ?>)exchange.getProperty(Client.REQUEST_CONTEXT));
        assertNotNull(requestContext);
        String actualValue = (String)requestContext.get(TEST_KEY);
        assertEquals("exchange property should get propagated to the request context", TEST_VALUE, actualValue);
    }

    @Override   
    protected String getSimpleEndpointUri() {
        return "cxf://http://localhost:" + CXFTestSupport.getPort4() + "/CxfProducerContextTest/simple?serviceClass=org.apache.camel.component.cxf.HelloService";
    }

    @Override   
    protected String getJaxwsEndpointUri() {
        return "cxf://http://localhost:" + CXFTestSupport.getPort4() + "/CxfProducerContextTest/jaxws?serviceClass=org.apache.hello_world_soap_http.Greeter";
    }
    
    @Override   
    protected Exchange sendSimpleMessage() {
        Exchange exchange = template.send(getSimpleEndpointUri(), new Processor() {
            public void process(final Exchange exchange) {
                final List<String> params = new ArrayList<String>();
                params.add(TEST_MESSAGE);
                Map<String, Object> requestContext = new HashMap<String, Object>();
                requestContext.put(Message.ENDPOINT_ADDRESS, getSimpleServerAddress());
                exchange.getIn().setBody(params);
                exchange.getIn().setHeader(Client.REQUEST_CONTEXT, requestContext);
                exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, ECHO_OPERATION);
                exchange.getIn().setHeader(Exchange.FILE_NAME, "testFile");
                exchange.getIn().setHeader("requestObject", new DefaultCxfBinding());
                exchange.getProperties().put(TEST_KEY, TEST_VALUE);
            }
        });
        return exchange;

    }
    
    @Override   
    protected Exchange sendJaxWsMessage() {
        Exchange exchange = template.send(getJaxwsEndpointUri(), new Processor() {
            public void process(final Exchange exchange) {
                final List<String> params = new ArrayList<String>();
                params.add(TEST_MESSAGE);
                Map<String, Object> requestContext = new HashMap<String, Object>();
                requestContext.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, getJaxWsServerAddress());
                exchange.getIn().setBody(params);
                exchange.getIn().setHeader(Client.REQUEST_CONTEXT, requestContext);
                exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, GREET_ME_OPERATION);
                exchange.getIn().setHeader(Exchange.FILE_NAME, "testFile");
            }
        });
        return exchange;
    }
    
}
