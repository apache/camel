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
import javax.xml.ws.Endpoint;

import org.apache.camel.builder.RouteBuilder;
import org.apache.hello_world_soap_http.GreeterImpl;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * The Greeter test with the PAYLOAD date format
 */
public class CxfGreeterPayLoadRouterTest  extends AbstractCXFGreeterRouterTest {
    protected static Endpoint endpoint;
    @AfterClass
    public static void stopService() {
        if (endpoint != null) {
            endpoint.stop();
        }
    }

    @BeforeClass
    public static void startService() {
        Object implementor = new GreeterImpl();
        String address = "http://localhost:" + getPort1() + "/CxfGreeterPayLoadRouterTest/SoapContext/SoapPort";
        endpoint = Endpoint.publish(address, implementor); 
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("cxf:bean:routerEndpoint?dataFormat=PAYLOAD&publishedEndpointUrl=http://www.simple.com/services/test")
                    .to("cxf:bean:serviceEndpoint?dataFormat=PAYLOAD");
            }
        };
    }

    
    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/GreeterEndpointPayloadBeans.xml");
    }



}
