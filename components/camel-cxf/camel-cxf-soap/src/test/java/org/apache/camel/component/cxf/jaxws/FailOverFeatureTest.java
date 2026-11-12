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
package org.apache.camel.component.cxf.jaxws;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.cxf.clustering.FailoverFeature;
import org.apache.cxf.clustering.RandomStrategy;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.AbstractWSDLBasedEndpointFactory;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FailOverFeatureTest {
    private static AvailablePortFinder.Port SERVICE_PORT;
    private static AvailablePortFinder.Port PAYLOAD_PROXY_PORT;
    private static AvailablePortFinder.Port POJO_PROXY_PORT;
    private static AvailablePortFinder.Port NONE_EXIST_PORT;

    private static void allocatePorts() {
        SERVICE_PORT = AvailablePortFinder.find();
        PAYLOAD_PROXY_PORT = AvailablePortFinder.find();
        POJO_PROXY_PORT = AvailablePortFinder.find();
        NONE_EXIST_PORT = AvailablePortFinder.find();
        SERVICE_ADDRESS = "http://localhost:" + SERVICE_PORT.getPort() + "/FailOverFeatureTest";
        PAYLOAD_PROXY_ADDRESS = "http://localhost:" + PAYLOAD_PROXY_PORT.getPort() + "/FailOverFeatureTest/proxy";
        POJO_PROXY_ADDRESS = "http://localhost:" + POJO_PROXY_PORT.getPort() + "/FailOverFeatureTest/proxy";
        NONE_EXIST_ADDRESS = "http://localhost:" + NONE_EXIST_PORT.getPort() + "/FailOverFeatureTest";
    }

    @AfterAll
    static void releasePorts() {
        if (SERVICE_PORT != null) {
            SERVICE_PORT.release();
        }
        if (PAYLOAD_PROXY_PORT != null) {
            PAYLOAD_PROXY_PORT.release();
        }
        if (POJO_PROXY_PORT != null) {
            POJO_PROXY_PORT.release();
        }
        if (NONE_EXIST_PORT != null) {
            NONE_EXIST_PORT.release();
        }
    }

    private static String SERVICE_ADDRESS;
    private static String PAYLOAD_PROXY_ADDRESS;
    private static String POJO_PROXY_ADDRESS;
    private static String NONE_EXIST_ADDRESS;
    private static Server server;
    private DefaultCamelContext context1;
    private DefaultCamelContext context2;

    @AfterEach
    public void cleanupContexts() {
        if (context1 != null) {
            try {
                context1.stop();
            } catch (Exception e) {
                // ignore
            }
            context1 = null;
        }
        if (context2 != null) {
            try {
                context2.stop();
            } catch (Exception e) {
                // ignore
            }
            context2 = null;
        }
    }

    @BeforeAll
    public static void init() {
        allocatePorts();

        // publish a web-service
        ServerFactoryBean factory = new ServerFactoryBean();
        factory.setAddress(SERVICE_ADDRESS);
        factory.setServiceBean(new HelloServiceImpl());
        server = factory.create();
    }

    @AfterAll
    public static void destroy() {
        if (server != null) {
            server.stop();
            server.destroy();
        }
    }

    @Test
    public void testPojo() throws Exception {
        startRoutePojo();
        assertEquals("hello", tryFailover(POJO_PROXY_ADDRESS));
    }

    @Test
    public void testPayload() throws Exception {
        startRoutePayload();
        assertEquals("hello", tryFailover(PAYLOAD_PROXY_ADDRESS));
    }

    private void startRoutePayload() throws Exception {

        String proxy = "cxf://" + PAYLOAD_PROXY_ADDRESS + "?wsdlURL=" + SERVICE_ADDRESS + "?wsdl"
                       + "&dataFormat=PAYLOAD";

        // use a non-exists address to trigger fail-over
        // synchronous=true guards against CXF-9272 async callback hang on refused connections (CAMEL-26600, CAMEL-26696)
        String real = "cxf://" + NONE_EXIST_ADDRESS + "?wsdlURL=" + SERVICE_ADDRESS + "?wsdl"
                      + "&dataFormat=PAYLOAD&synchronous=true";

        context1 = new DefaultCamelContext();
        startRoute(context1, proxy, real);
    }

    private void startRoutePojo() throws Exception {

        String proxy = "cxf://" + POJO_PROXY_ADDRESS + "?serviceClass=" + "org.apache.camel.component.cxf.jaxws.HelloService"
                       + "&dataFormat=POJO";

        // use a non-exists address to trigger fail-over
        // synchronous=true guards against CXF-9272 async callback hang on refused connections (CAMEL-26600, CAMEL-26696)
        String real = "cxf://" + NONE_EXIST_ADDRESS + "?serviceClass=" + "org.apache.camel.component.cxf.jaxws.HelloService"
                      + "&dataFormat=POJO&synchronous=true";

        context2 = new DefaultCamelContext();
        startRoute(context2, proxy, real);
    }

    private void startRoute(final DefaultCamelContext ctx, final String proxy, final String real) throws Exception {

        ctx.addRoutes(new RouteBuilder() {
            public void configure() {
                String alt = SERVICE_ADDRESS;

                List<String> serviceList = new ArrayList<>();
                serviceList.add(alt);

                RandomStrategy strategy = new RandomStrategy();
                strategy.setAlternateAddresses(serviceList);

                FailoverFeature ff = new FailoverFeature();
                ff.setStrategy(strategy);

                CxfEndpoint endpoint = (CxfEndpoint) (ctx.getEndpoint(real));
                endpoint.getFeatures().add(ff);
                endpoint.setCxfConfigurer(new CxfConfigurer() {
                    @Override
                    public void configure(AbstractWSDLBasedEndpointFactory factoryBean) {
                    }

                    @Override
                    public void configureClient(Client client) {
                        HTTPConduit conduit = (HTTPConduit) client.getConduit();
                        HTTPClientPolicy policy = new HTTPClientPolicy();
                        policy.setConnectionTimeout(5000);
                        policy.setReceiveTimeout(5000);
                        conduit.setClient(policy);
                    }

                    @Override
                    public void configureServer(Server server) {
                    }
                });

                from(proxy).to(endpoint);
            }
        });
        ctx.start();

    }

    private String tryFailover(String url) {

        ClientProxyFactoryBean factory = new ClientProxyFactoryBean();

        factory.setServiceClass(HelloService.class);
        factory.setAddress(url);

        HelloService client = (HelloService) factory.create();
        return client.sayHello();
    }

}
