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
package org.apache.camel.dataformat.beanio;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeanIOSplitterCustomBeanReaderErrorHandlerTest extends CamelTestSupport {

    // START SNIPPET: e2
    private static final String FIXED_DATA =
            "Joe,Smith,Developer,75000,10012009" + Constants.LS
            + "Jane,Doe,Architect,80000,01152008" + Constants.LS
            + "Jon,Anderson,Manager,85000,03182007" + Constants.LS;
    // END SNIPPET: e2

    private static final String FIXED_FAIL_DATA =
            "Joe,Smith,Developer,75000,10012009" + Constants.LS
                    + "Jane,Doe,Architect,80000,01152008" + Constants.LS
                    + "Jon,Anderson,Manager,XXX,03182007" + Constants.LS;

    @Test
    void testSplit() throws Exception {
        List<Employee> employees = getEmployees();

        MockEndpoint mock = getMockEndpoint("mock:beanio-unmarshal");
        mock.expectedBodiesReceived(employees);

        template.sendBody("direct:unmarshal", FIXED_DATA);

        mock.assertIsSatisfied();
    }

    @Test
    void testSplitFail() throws Exception {
        // there should be 1 splitted that failed we get also
        MockEndpoint mock = getMockEndpoint("mock:beanio-unmarshal");
        mock.expectedMessageCount(3);
        mock.message(0).body().isInstanceOf(Employee.class);
        mock.message(1).body().isInstanceOf(Employee.class);
        mock.message(2).body().isInstanceOf(MyErrorDto.class);

        template.sendBody("direct:unmarshal", FIXED_FAIL_DATA);

        mock.assertIsSatisfied();

        assertEquals("employee", mock.getReceivedExchanges().get(2).getIn().getBody(MyErrorDto.class).getRecord());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                // setup beanio splitter using the mapping file, loaded from the classpath
                BeanIOSplitter splitter = new BeanIOSplitter();
                splitter.setMapping("org/apache/camel/dataformat/beanio/mappings.xml");
                splitter.setStreamName("employeeFile");
                splitter.setBeanReaderErrorHandlerType(MyErrorHandler.class);

                // a route which uses the bean io data format to format a CSV data
                // to java objects
                from("direct:unmarshal")
                    // and then split the message body so we get a message for each row
                    .split(splitter).streaming()
                        .to("log:line")
                        .to("mock:beanio-unmarshal");
                // END SNIPPET: e1
            }
        };
    }

    private List<Employee> getEmployees() throws ParseException {
        List<Employee> employees = new ArrayList<>();
        Employee one = new Employee();
        one.setFirstName("Joe");
        one.setLastName("Smith");
        one.setTitle("Developer");
        one.setSalary(75000);
        one.setHireDate(new SimpleDateFormat("MMddyyyy").parse("10012009"));
        employees.add(one);

        Employee two = new Employee();
        two.setFirstName("Jane");
        two.setLastName("Doe");
        two.setTitle("Architect");
        two.setSalary(80000);
        two.setHireDate(new SimpleDateFormat("MMddyyyy").parse("01152008"));
        employees.add(two);

        Employee three = new Employee();
        three.setFirstName("Jon");
        three.setLastName("Anderson");
        three.setTitle("Manager");
        three.setSalary(85000);
        three.setHireDate(new SimpleDateFormat("MMddyyyy").parse("03182007"));
        employees.add(three);
        return employees;
    }
}
