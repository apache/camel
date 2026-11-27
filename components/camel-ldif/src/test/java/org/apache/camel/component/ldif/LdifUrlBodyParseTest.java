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

import java.io.FileNotFoundException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * With allowUrlBody enabled, a body that is neither LDIF nor a URL fails with an {@link InvalidPayloadException},
 * whatever the URL parsing rejects it for. A URL that cannot be fetched is not a payload problem and keeps its own
 * exception. All of this happens before an LDAP connection is used, so this test needs no LDAP server.
 */
class LdifUrlBodyParseTest extends CamelTestSupport {

    @Test
    void bodyWithIllegalUriCharactersIsAnInvalidPayload() {
        CamelExecutionException ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:ldif", "not an ldif body"));
        InvalidPayloadException cause = assertInstanceOf(InvalidPayloadException.class, ex.getCause());
        assertInstanceOf(IllegalArgumentException.class, cause.getCause());
    }

    @Test
    void relativeBodyIsAnInvalidPayload() {
        CamelExecutionException ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:ldif", "foo"));
        InvalidPayloadException cause = assertInstanceOf(InvalidPayloadException.class, ex.getCause());
        assertInstanceOf(IllegalArgumentException.class, cause.getCause());
    }

    @Test
    void unknownUrlSchemeIsAnInvalidPayload() {
        CamelExecutionException ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:ldif", "unknownscheme://example.com/file.ldif"));
        assertInstanceOf(InvalidPayloadException.class, ex.getCause());
    }

    @Test
    void urlThatCannotBeFetchedKeepsItsException() {
        CamelExecutionException ex = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:ldif", "file:/does/not/exist/camel-ldif-test.ldif"));
        assertInstanceOf(FileNotFoundException.class, ex.getCause());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:ldif").to("ldif:myConnection?allowUrlBody=true");
            }
        };
    }
}
