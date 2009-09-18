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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.wsdl_first.Person;
import org.apache.camel.wsdl_first.PersonImpl;
import org.apache.camel.wsdl_first.PersonService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CXFWsdlOnlyPayloadModeNoSpringTest extends CamelTestSupport {
    
    private Endpoint endpoint;

    @Before
    public void startService() {
        endpoint = Endpoint.publish("http://localhost:8092/PersonService/", new PersonImpl());
    }
    
    @After
    public void stopService() {
        if (endpoint != null) {
            endpoint.stop();
        }

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("cxf://http://localhost:8092/PersonService?wsdlURL=classpath:person.wsdl&dataFormat=" + getDataFormat())
                    .to("cxf://http://localhost:8093/PersonService?wsdlURL=classpath:person.wsdl&dataFormat=" + getDataFormat());
            }
        };
    }
 
    protected String getDataFormat() {
        return "PAYLOAD";
    }

    @Test
    public void testRoutes() throws Exception {
        URL wsdlURL = getClass().getClassLoader().getResource("person.wsdl");
        PersonService ss = new PersonService(wsdlURL, new QName("http://camel.apache.org/wsdl-first",
                                                                "PersonService"));
        Person client = ss.getSoap();
        Holder<String> personId = new Holder<String>();
        personId.value = "hello";
        Holder<String> ssn = new Holder<String>();
        Holder<String> name = new Holder<String>();
        client.getPerson(personId, ssn, name);
        assertEquals("Bonjour", name.value);

    }
    
}
