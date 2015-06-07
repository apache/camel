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
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.cxf.clustering.LoadDistributorFeature;
import org.apache.cxf.clustering.SequentialStrategy;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class LoadDistributorFeatureTest {
    
    private static int port1 = CXFTestSupport.getPort1(); 
    private static int port2 = CXFTestSupport.getPort2();
    private static int port3 = CXFTestSupport.getPort3();
    
    
    private static final String SERVICE_ADDRESS_1 = "http://localhost:" + port1 + "/LoadDistributorFeatureTest/service1";
    private static final String SERVICE_ADDRESS_2 = "http://localhost:" + port1 + "/LoadDistributorFeatureTest/service2";
    private static final String PAYLOAD_PROXY_ADDRESS = "http://localhost:" + port2 + "/LoadDistributorFeatureTest/proxy";
    private static final String POJO_PROXY_ADDRESS = "http://localhost:" + port3 + "/LoadDistributorFeatureTest/proxy";
    
    private DefaultCamelContext context1;
    private DefaultCamelContext context2;

    @BeforeClass
    public static void init() {

        // publish two web-service
        ServerFactoryBean factory1 = new ServerFactoryBean();
        factory1.setAddress(SERVICE_ADDRESS_1);
        factory1.setServiceBean(new HelloServiceImpl(" Server1"));
        factory1.create();
        
        ServerFactoryBean factory2 = new ServerFactoryBean();
        factory2.setAddress(SERVICE_ADDRESS_2);
        factory2.setServiceBean(new HelloServiceImpl(" Server2"));
        factory2.create();
    }

    @Test
    public void testPojo() throws Exception {
        startRoutePojo();
        Assert.assertEquals("hello Server1", tryLoadDistributor(POJO_PROXY_ADDRESS));
        Assert.assertEquals("hello Server2", tryLoadDistributor(POJO_PROXY_ADDRESS));
        if (context2 != null) {
            context2.stop();
        }
    }

    @Test
    public void testPayload() throws Exception {
        startRoutePayload();
        Assert.assertEquals("hello Server1", tryLoadDistributor(PAYLOAD_PROXY_ADDRESS));
        Assert.assertEquals("hello Server2", tryLoadDistributor(PAYLOAD_PROXY_ADDRESS));
        if (context1 != null) {
            context1.stop();
        }
    }

    private void startRoutePayload() throws Exception {

        String proxy = "cxf://" + PAYLOAD_PROXY_ADDRESS + "?wsdlURL=" + SERVICE_ADDRESS_1 + "?wsdl"
                       + "&dataFormat=PAYLOAD";

        String backend = "cxf://" + SERVICE_ADDRESS_1 + "?wsdlURL=" + SERVICE_ADDRESS_1 + "?wsdl"
                      + "&dataFormat=PAYLOAD";

        context1 = new DefaultCamelContext();
        startRoute(context1, proxy, backend);
    }

    private void startRoutePojo() throws Exception {

        String proxy = "cxf://" + POJO_PROXY_ADDRESS + "?serviceClass=" + "org.apache.camel.component.cxf.HelloService"
                       + "&dataFormat=POJO";


        String backend = "cxf://" + SERVICE_ADDRESS_1 + "?serviceClass=" + "org.apache.camel.component.cxf.HelloService"
                      + "&dataFormat=POJO";

        context2 = new DefaultCamelContext();
        startRoute(context2, proxy, backend);
    }

    private void startRoute(DefaultCamelContext ctx, final String proxy, final String real) throws Exception {

        ctx.addRoutes(new RouteBuilder() {
            public void configure() {
                
                List<String> serviceList = new ArrayList<String>();
                serviceList.add(SERVICE_ADDRESS_1);
                serviceList.add(SERVICE_ADDRESS_2);

                SequentialStrategy strategy = new SequentialStrategy();
                strategy.setAlternateAddresses(serviceList);

                LoadDistributorFeature ldf = new LoadDistributorFeature();
                ldf.setStrategy(strategy);

                CxfEndpoint endpoint = (CxfEndpoint)(endpoint(real));
                endpoint.getFeatures().add(ldf);

                from(proxy).to(endpoint);
            }
        });
        ctx.start();

    }

    private String tryLoadDistributor(String url) {

        ClientProxyFactoryBean factory = new ClientProxyFactoryBean();

        factory.setServiceClass(HelloService.class);
        factory.setAddress(url);


        HelloService client = (HelloService) factory.create();
        return client.sayHello();
    }


}
