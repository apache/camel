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
package org.apache.camel.component.smooks;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.smooks.Customer;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the Smooks component does not resolve external XML entities from the message body by default, and that
 * the legacy behaviour can be explicitly restored with {@code allowExternalEntities=true}.
 */
public class SmooksComponentXmlSecurityTest extends CamelTestSupport {

    private static final String MARKER = "SECRET_MARKER_do_not_leak";

    @TempDir
    Path tempDir;

    private Path secretFile;

    @BeforeEach
    public void writeSecret() throws Exception {
        secretFile = tempDir.resolve("secret.txt");
        Files.writeString(secretFile, MARKER);
    }

    @Test
    public void externalEntityNotResolvedByDefault() {
        String firstName;
        try {
            Customer customer = template.requestBody("direct:secure", xxePayload(), Customer.class);
            firstName = customer != null && customer.getFirstName() != null ? customer.getFirstName() : "";
        } catch (Exception e) {
            // A parser that rejects the external entity reference is the expected secure behaviour
            firstName = "";
        }
        assertFalse(firstName.contains(MARKER),
                "External entity must not be resolved by default, but file content leaked into the bean");
    }

    @Test
    public void externalEntityResolvedWhenExplicitlyAllowed() {
        Customer customer = template.requestBody("direct:insecure", xxePayload(), Customer.class);
        // The opt-in restores the legacy behaviour; this also proves the marker file is genuinely readable, so the
        // secure test above is meaningful
        assertTrue(customer.getFirstName().contains(MARKER),
                "With allowExternalEntities=true the external entity should be resolved (control assertion)");
    }

    private String xxePayload() {
        String systemId = secretFile.toUri().toString();
        return "<!DOCTYPE customer [<!ENTITY xxe SYSTEM \"" + systemId + "\">]>\n"
               + "<customer><firstName>&xxe;</firstName><lastName>d</lastName>"
               + "<gender>Male</gender><age>1</age><country>x</country></customer>";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:secure").to("smooks://smooks-config.xml");
                from("direct:insecure").to("smooks://smooks-config.xml?allowExternalEntities=true");
            }
        };
    }
}
