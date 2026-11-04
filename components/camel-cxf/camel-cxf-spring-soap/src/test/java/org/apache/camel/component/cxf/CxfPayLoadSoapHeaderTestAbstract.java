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

import jakarta.xml.ws.Endpoint;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

public class CxfPayLoadSoapHeaderTestAbstract extends CamelTestSupport {

    private static AvailablePortFinder.Port PORT1;
    private static AvailablePortFinder.Port PORT2;

    @BeforeAll
    static void allocateCxfPayLoadSoapHeaderTestAbstractPorts() {
        PORT1 = AvailablePortFinder.find();
        System.setProperty("CxfPayLoadSoapHeaderTestAbstract.port1", PORT1.toString());
        PORT2 = AvailablePortFinder.find();
        System.setProperty("CxfPayLoadSoapHeaderTestAbstract.port2", PORT2.toString());
        port1 = PORT1.getPort();
        port2 = PORT2.getPort();
    }

    @AfterAll
    static void releaseCxfPayLoadSoapHeaderTestAbstractPorts() {
        System.clearProperty("CxfPayLoadSoapHeaderTestAbstract.port1");
        if (PORT1 != null) {
            PORT1.release();
        }
        System.clearProperty("CxfPayLoadSoapHeaderTestAbstract.port2");
        if (PORT2 != null) {
            PORT2.release();
        }
    }

    static int port1;
    static int port2;

    protected String getRouterEndpointURI() {
        return "cxf:http://localhost:" + port1 + "/" + getClass().getSimpleName()
               + "/pizza_service/services/PizzaService?wsdlURL=classpath:pizza_service.wsdl&dataFormat=PAYLOAD";
    }

    protected String getServiceEndpointURI() {
        return "cxf:http://localhost:" + port2 + "/" + getClass().getSimpleName()
               + "/new_pizza_service/services/PizzaService?wsdlURL=classpath:pizza_service.wsdl&dataFormat=PAYLOAD";
    }

    protected void start(String n) {
        Object implementor = new PizzaImpl();
        String address = "http://localhost:" + port2 + "/" + n
                         + "/new_pizza_service/services/PizzaService";
        Endpoint.publish(address, implementor);
    }

    @BeforeEach
    public void startService() {
        start(getClass().getSimpleName());
    }

}
