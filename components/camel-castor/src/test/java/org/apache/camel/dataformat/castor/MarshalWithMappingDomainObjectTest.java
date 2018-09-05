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
package org.apache.camel.dataformat.castor;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Marhsal tests with domain objects.
 */
public class MarshalWithMappingDomainObjectTest extends CamelTestSupport {

    @Test
    public void testMarshalDomainObject() throws Exception {
        // some platform cannot test using Castor as it uses a SUN dependent Xerces
        if (isJavaVendor("IBM")) {
            return;
        }

        MockEndpoint mock = getMockEndpoint("mock:marshal");
        mock.expectedMessageCount(1);

        Student student = new Student();
        student.setStuLastName("Dilshan");
        student.setStuAge(25);
        template.sendBody("direct:marshal", student);

        mock.assertIsSatisfied();
    }

    @Test
    public void testUnmarshalDomainObject() throws Exception {
        // some platform cannot test using Castor as it uses a SUN dependent Xerces
        if (isJavaVendor("IBM")) {
            return;
        }

        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
        mock.expectedMessageCount(1);

        String xml = "<student><firstname>Sagara</firstname><lastname>Gunathunga</lastname><age>27</age></student>";
        template.sendBody("direct:unmarshal", xml);

        Student student = new Student();
        student.setStuLastName("Gunathunga");
        student.setStuFirstName("Sagara");
        student.setStuAge(27);

        mock.assertIsSatisfied();
        mock.message(0).body().isInstanceOf(Student.class);
        mock.message(0).body().isEqualTo(student);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {

                CastorDataFormat myformat = new CastorDataFormat();
                myformat.setMappingFile("map.xml");
                myformat.setValidation(true);
                myformat.setAllowClasses(Student.class);
                
                from("direct:marshal").marshal(myformat).to("mock:marshal");
                from("direct:unmarshal").unmarshal(myformat).to("mock:unmarshal");

            }
        };
    }

}