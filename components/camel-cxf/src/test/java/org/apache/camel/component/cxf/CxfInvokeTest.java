/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.cxf;

import junit.framework.TestCase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.CamelTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.cxf.endpoint.ServerImpl;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.bus.CXFBusFactory;

import java.util.List;
import java.util.ArrayList;

/**
 * @version $Revision$
 */
public class CxfInvokeTest extends TestCase {
    private static final transient Log log = LogFactory.getLog(CxfInvokeTest.class);
    protected CamelContext camelContext = new DefaultCamelContext();
    protected CamelTemplate<CxfExchange> template = new CamelTemplate<CxfExchange>(camelContext);

    final private String transportAddress = "http://localhost:28080/test";
    final private String testMessage = "Hello World!";
    private ServerImpl server;

    @Override
    protected void setUp() throws Exception {

        // start a service
        ServerFactoryBean svrBean = new ServerFactoryBean();

        svrBean.setAddress(transportAddress);
        svrBean.setServiceClass(HelloServiceImpl.class);
        svrBean.setBus(CXFBusFactory.getDefaultBus());

        server = (ServerImpl)svrBean.create();
        server.start();
    }

    @Override
    protected void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    public void testInvokeOfServer() throws Exception {

        CxfExchange exchange =
            template.send(getUri(),
                        new Processor() {
                            public void process(final Exchange exchange) {
                                final List<String> params = new ArrayList<String>();
                                params.add(testMessage);
                                exchange.getIn().setBody(params);
                            }
                        });

        org.apache.camel.Message out = exchange.getOut();

        Object[] output = (Object[])out.getBody();
        log.info("Received output text: " + output[0]);

        assertEquals("reply body on Camel", testMessage, output[0]);
    }

    private String getUri() {
        return "cxf-invoke:" + transportAddress
            + "?sei=org.apache.camel.component.cxf.HelloService&method=echo";
    }
}
