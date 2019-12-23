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
package org.apache.camel.component.jdbc;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class JdbcGeneratedKeysTest extends AbstractJdbcGeneratedKeysTest {

    @Test
    public void testRetrieveGeneratedKeys() throws Exception {
        super.testRetrieveGeneratedKeys("insert into tableWithAutoIncr (content) values ('value2')");
    }

    @Test
    public void testRetrieveGeneratedKeysWithStringGeneratedColumns() throws Exception {
        super.testRetrieveGeneratedKeysWithStringGeneratedColumns("insert into tableWithAutoIncr (content) values ('value2')");
    }

    @Test
    public void testRetrieveGeneratedKeysWithIntGeneratedColumns() throws Exception {
        super.testRetrieveGeneratedKeysWithIntGeneratedColumns("insert into tableWithAutoIncr (content) values ('value2')");
    }

    @Test
    public void testGivenAnInvalidGeneratedColumnsHeaderThenAnExceptionIsThrown() throws Exception {
        super.testGivenAnInvalidGeneratedColumnsHeaderThenAnExceptionIsThrown("insert into tableWithAutoIncr (content) values ('value2')");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            // START SNIPPET: route
            // lets add simple route
            public void configure() throws Exception {
                from("direct:hello").to("jdbc:testdb?readSize=100");
            }
            // END SNIPPET: route
        };
    }

}
