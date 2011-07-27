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

import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Holder;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.wsdl_first.JaxwsTestHandler;
import org.apache.camel.wsdl_first.Person;
import org.apache.camel.wsdl_first.PersonImpl;
import org.apache.camel.wsdl_first.PersonService;
import org.apache.camel.wsdl_first.UnknownPersonFault;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfWsdlFirstPayloadModeTest extends AbstractCxfWsdlFirstTest {


    @BeforeClass
    public static void startService() {
        Object implementor = new PersonImpl();
        String address = "http://localhost:" + getPort1() 
            + "/CxfWsdlFirstPayloadModeTest/PersonService/";
        Endpoint.publish(address, implementor);
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/WsdlFirstBeansPayloadMode.xml");
    }
    

    @Test
    public void testInvokingServiceWithCamelProducer() throws Exception {
        // this test does not apply to PAYLOAD mode
    }
    


}
