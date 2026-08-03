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
package org.apache.camel.component.ldif;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A non-LDIF body (one that does not start with {@code version: 1}) is dereferenced as a URL only when
 * {@code allowUrlBody} is enabled. By default it is rejected instead of being fetched, which avoids a content-sniffed
 * URL fetch (SSRF) from untrusted body content. The rejection happens before any LDAP connection is used, so this test
 * needs no LDAP server. See CAMEL-24297.
 */
class LdifAllowUrlBodyTest extends CamelTestSupport {

    @Test
    void nonLdifBodyIsRejectedByDefault() {
        CamelExecutionException ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:ldif", "http://example.com/evil.ldif"));
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("allowUrlBody"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:ldif").to("ldif:myConnection");
            }
        };
    }
}
