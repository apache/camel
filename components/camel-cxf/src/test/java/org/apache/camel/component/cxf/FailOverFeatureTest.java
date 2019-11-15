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
package org.apache.camel.component.cxf;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.cxf.clustering.FailoverFeature;
import org.apache.cxf.clustering.RandomStrategy;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class FailOverFeatureTest {
    
    private static int port1 = CXFTestSupport.getPort1(); 
    private static int port2 = CXFTestSupport.getPort2();
    private static int port3 = CXFTestSupport.getPort3();
    private static int port4 = AvailablePortFinder.getNextAvailable(); 
    
    private static final String SERVICE_ADDRESS = "http://localhost:" + port1 + "/FailOverFeatureTest";
    private static final String PAYLOAD_PROXY_ADDRESS = "http://localhost:" + port2 + "/FailOverFeatureTest/proxy";
    private static final String POJO_PROXY_ADDRESS = "http://localhost:" + port3 + "/FailOverFeatureTest/proxy";
    private static final String NONE_EXIST_ADDRESS = "http://localhost:" + port4 + "/FailOverFeatureTest";
    private DefaultCamelContext context1;
    private DefaultCamelContext context2;

    @BeforeClass
    public static void init() {

        // publish a web-service
        ServerFactoryBean factory = new ServerFactoryBean();
        factory.setAddress(SERVICE_ADDRESS);
        factory.setServiceBean(new HelloServiceImpl());
        factory.create();
    }

    @Test
    public void testPojo() throws Exception {
        startRoutePojo();
        Assert.assertEquals("hello", tryFailover(POJO_PROXY_ADDRESS));
        if (context2 != null) {
            context2.stop();
        }
    }

    @Test
    public void testPayload() throws Exception {
        startRoutePayload();
        Assert.assertEquals("hello", tryFailover(PAYLOAD_PROXY_ADDRESS));
        if (context1 != null) {
            context1.stop();
        }
    }

    private void startRoutePayload() throws Exception {

        String proxy = "cxf://" + PAYLOAD_PROXY_ADDRESS + "?wsdlURL=" + SERVICE_ADDRESS + "?wsdl"
                       + "&dataFormat=PAYLOAD";

        // use a non-exists address to trigger fail-over
        // another problem is: if synchronous=false fail-over will not happen
        String real = "cxf://" + NONE_EXIST_ADDRESS + "?wsdlURL=" + SERVICE_ADDRESS + "?wsdl"
                      + "&dataFormat=PAYLOAD";

        context1 = new DefaultCamelContext();
        startRoute(context1, proxy, real);
    }

    private void startRoutePojo() throws Exception {

        String proxy = "cxf://" + POJO_PROXY_ADDRESS + "?serviceClass=" + "org.apache.camel.component.cxf.HelloService"
                       + "&dataFormat=POJO";

        // use a non-exists address to trigger fail-over
        String real = "cxf://" + NONE_EXIST_ADDRESS + "?serviceClass=" + "org.apache.camel.component.cxf.HelloService"
                      + "&dataFormat=POJO";

        context2 = new DefaultCamelContext();
        startRoute(context2, proxy, real);
    }

    private void startRoute(DefaultCamelContext ctx, final String proxy, final String real) throws Exception {

        ctx.addRoutes(new RouteBuilder() {
            public void configure() {
                String alt = SERVICE_ADDRESS;

                List<String> serviceList = new ArrayList<>();
                serviceList.add(alt);

                RandomStrategy strategy = new RandomStrategy();
                strategy.setAlternateAddresses(serviceList);

                FailoverFeature ff = new FailoverFeature();
                ff.setStrategy(strategy);

                CxfEndpoint endpoint = (CxfEndpoint)(endpoint(real));
                endpoint.getFeatures().add(ff);

                from(proxy).to(endpoint);
            }
        });
        ctx.start();

    }

    private String tryFailover(String url) {

        ClientProxyFactoryBean factory = new ClientProxyFactoryBean();

        factory.setServiceClass(HelloService.class);
        factory.setAddress(url);

        HelloService client = (HelloService)factory.create();
        return client.sayHello();
    }

}
