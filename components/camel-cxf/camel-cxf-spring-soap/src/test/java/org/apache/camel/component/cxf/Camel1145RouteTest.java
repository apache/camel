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

import java.net.URL;

import javax.xml.namespace.QName;

import org.apache.camel.non_wrapper.Person;
import org.apache.camel.non_wrapper.PersonService;
import org.apache.camel.non_wrapper.types.GetPerson;
import org.apache.camel.non_wrapper.types.GetPersonResponse;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ContextConfiguration(locations = { "/org/apache/camel/component/cxf/context-camel-1145.xml" })
@ExtendWith(SpringExtension.class)
public class Camel1145RouteTest {
    private static int port;

    public static int portNotAvailableHandler(boolean allocated) {
        assumeTrue(allocated);
        return -1;
    }

    @BeforeAll
    public static void getPort() {
        port = AvailablePortFinder.getSpecificPort(9000, false, Camel1145RouteTest::portNotAvailableHandler);
    }

    @Test
    public void testCamel1145Route() throws Exception {
        URL wsdlURL = new URL(String.format("http://localhost:%d/PersonService/?wsdl", port));
        PersonService ss = new PersonService(wsdlURL, new QName("http://camel.apache.org/non-wrapper", "PersonService"));
        Person client = ss.getSoap();
        GetPerson request = new GetPerson();
        request.setPersonId("hello");
        GetPersonResponse response = client.getPerson(request);

        assertEquals("Bill", response.getName(), "we should get the right answer from router");
        assertEquals("Test", response.getSsn(), "we should get the right answer from router");
        assertEquals("hello world!", response.getPersonId(), "we should get the right answer from router");

    }
}
