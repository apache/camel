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
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.message.Message;

// We use context to change the producer's endpoint address here
public class CxfProducerContextTest extends CxfProducerTest {


    protected String getSimpleEndpointUri() {
        return "cxf://http://localhost:9000/simple?serviceClass=org.apache.camel.component.cxf.HelloService";
    }

    protected String getJaxwsEndpointUri() {
        return "cxf://http://localhost:9000/jaxws?serviceClass=org.apache.hello_world_soap_http.Greeter";

    }
    protected CxfExchange sendSimpleMessage() {
        CxfExchange exchange = (CxfExchange)template.send(getSimpleEndpointUri(), new Processor() {
            public void process(final Exchange exchange) {
                final List<String> params = new ArrayList<String>();
                params.add(TEST_MESSAGE);
                Map<String, Object> requestContext = new HashMap<String, Object>();
                requestContext.put(Message.ENDPOINT_ADDRESS, SIMPLE_SERVER_ADDRESS);
                exchange.getIn().setBody(params);
                exchange.getIn().setHeader(Client.REQUEST_CONTEXT , requestContext);
                exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, ECHO_OPERATION);
            }
        });
        return exchange;

    }
    protected CxfExchange sendJaxWsMessage() {
        CxfExchange exchange = (CxfExchange)template.send(getJaxwsEndpointUri(), new Processor() {
            public void process(final Exchange exchange) {
                final List<String> params = new ArrayList<String>();
                params.add(TEST_MESSAGE);
                Map<String, Object> requestContext = new HashMap<String, Object>();
                requestContext.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, JAXWS_SERVER_ADDRESS);
                exchange.getIn().setBody(params);
                exchange.getIn().setHeader(Client.REQUEST_CONTEXT , requestContext);
                exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, GREET_ME_OPERATION);
            }
        });
        return exchange;
    }
}
