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
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The SQL of the body is mandatory.
 */
public class JdbcNoBodyTest extends AbstractJdbcTestSupport {

    @Test
    public void testNoBody() {
        assertNoBody("direct:plain");
    }

    @Test
    public void testNoBodyWithHeadersAsParameters() {
        assertNoBody("direct:headers");
    }

    @Test
    public void testNoBodyWithoutResetAutoCommit() {
        assertNoBody("direct:noReset");
    }

    private void assertNoBody(String uri) {
        CamelExecutionException e = assertThrows(CamelExecutionException.class, () -> template.sendBody(uri, null));
        assertInstanceOf(InvalidPayloadException.class, e.getCause());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:plain").to("jdbc:testdb");
                from("direct:headers").to("jdbc:testdb?useHeadersAsParameters=true");
                from("direct:noReset").to("jdbc:testdb?resetAutoCommit=false");
            }
        };
    }
}
