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
package org.apache.camel.lanaguage.sql;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.sql.Person;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 *
 */
public class SqlResourceTest extends CamelTestSupport {

    @Test
    public void testSqlResource() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedHeaderReceived("foo", 123);
        getMockEndpoint("mock:name").expectedBodiesReceived("Hiram");

        template.sendBody("direct:start", createBody());

        assertMockEndpointsSatisfied();
    }

    @SuppressWarnings({"rawtypes"})
    private List createBody() {
        List<Person> list = new ArrayList<Person>();
        list.add(new Person("James", "London"));
        list.add(new Person("Guillaume", "Normandy"));
        list.add(new Person("Hiram", "Tampa"));
        list.add(new Person("Rob", "London"));
        return list;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .setHeader("foo", constant(123))
                        .transform().sql("resource:classpath:myjosql.txt")
                        .to("mock:result")
                        // the result from josql is a spreadsheet eg list<list> so we grab the 1st data
                        .transform().simple("${body[0][0]}")
                        .to("mock:name");
            }
        };
    }
}
