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

import javax.xml.ws.Endpoint;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;

public class CxfPayLoadSoapHeaderTestAbstract extends CamelTestSupport {
    static int port1 = CXFTestSupport.getPort1(); 
    static int port2 = CXFTestSupport.getPort2(); 
    
    protected String getRouterEndpointURI() {
        return "cxf:http://localhost:" + port1 + "/" + getClass().getSimpleName() 
            + "/pizza_service/services/PizzaService?wsdlURL=classpath:pizza_service.wsdl&dataFormat=PAYLOAD";
    }
    protected String getServiceEndpointURI() {
        return "cxf:http://localhost:" + port2 + "/" + getClass().getSimpleName()
            + "/new_pizza_service/services/PizzaService?wsdlURL=classpath:pizza_service.wsdl&dataFormat=PAYLOAD";
    }     
    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

 
    protected void start(String n) {
        Object implementor = new PizzaImpl();
        String address = "http://localhost:" + port2 + "/" + n
            + "/new_pizza_service/services/PizzaService";
        Endpoint.publish(address, implementor);        
    }
    
    @Before
    public void startService() {
        start(getClass().getSimpleName());
    }
    

}
