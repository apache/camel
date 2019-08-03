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

import org.apache.camel.Exchange;
import org.apache.camel.wsdl_first.PersonImpl12;
import org.junit.Before;

public class CXFWsdlOnlyPayloadModeNoSpringSoap12Test extends CXFWsdlOnlyPayloadModeNoSpringTest {
    
    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    @Override
    @Before
    public void startService() {
        endpoint = Endpoint.publish("http://localhost:" + port1 + "/" 
            + getClass().getSimpleName() 
            + "/PersonService", new PersonImpl12());
    }
    
    @Override
    protected String getServiceName() {
        return "{http://camel.apache.org/wsdl-first}PersonService12";
    }
    
    @Override
    protected void checkSOAPAction(Exchange exchange) {
        assertEquals(exchange.getIn().getHeader("SOAPAction"), "GetPersonAction");
    }
    
}
