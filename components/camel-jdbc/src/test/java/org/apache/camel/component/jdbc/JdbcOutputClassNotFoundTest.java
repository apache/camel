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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JdbcOutputClassNotFoundTest extends AbstractJdbcTestSupport {

    @Test
    public void testSelectListOutputClassNotFound() {
        assertOutputClassNotFound("direct:list");
    }

    @Test
    public void testSelectOneOutputClassNotFound() {
        assertOutputClassNotFound("direct:one");
    }

    private void assertOutputClassNotFound(String uri) {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody(uri, "select * from customer where ID = 'cust1'"));
        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(cause.getMessage().contains("org.example.NoSuchItem"), cause.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:list").to("jdbc:testdb?outputType=SelectList&outputClass=org.example.NoSuchItem");
                from("direct:one").to("jdbc:testdb?outputType=SelectOne&outputClass=org.example.NoSuchItem");
            }
        };
    }
}
