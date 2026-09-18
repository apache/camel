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
package org.apache.camel.dataformat.smooks;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.processor.UnmarshalProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the {@link SmooksDataFormat} does not resolve external XML entities from the message body by default,
 * and that the legacy behaviour can be explicitly restored with {@code allowExternalEntities=true}.
 */
public class SmooksDataFormatXmlSecurityTest {

    private static final String SMOOKS_CONFIG = "/smooks-config.xml";
    private static final String MARKER = "SECRET_MARKER_do_not_leak";

    @TempDir
    Path tempDir;

    private DefaultCamelContext camelContext;
    private Path secretFile;

    @BeforeEach
    public void beforeEach() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.start();
        secretFile = tempDir.resolve("secret.txt");
        Files.writeString(secretFile, MARKER);
    }

    @AfterEach
    public void afterEach() {
        camelContext.stop();
    }

    @Test
    public void externalEntityNotResolvedByDefault() throws Exception {
        SmooksDataFormat dataFormat = newDataFormat(false);
        try {
            String firstName = unmarshalFirstNameSafely(dataFormat, xxePayload());
            // Whether the parser rejects the external entity outright or yields empty content, the file must never leak
            assertFalse(firstName.contains(MARKER),
                    "External entity must not be resolved by default, but file content leaked into the bean");
        } finally {
            dataFormat.stop();
        }
    }

    @Test
    public void externalEntityResolvedWhenExplicitlyAllowed() throws Exception {
        SmooksDataFormat dataFormat = newDataFormat(true);
        try {
            String firstName = unmarshalFirstName(dataFormat, xxePayload());
            // The opt-in restores the legacy behaviour; this also proves the marker file is genuinely readable, so the
            // secure test above is meaningful
            assertTrue(firstName.contains(MARKER),
                    "With allowExternalEntities=true the external entity should be resolved (control assertion)");
        } finally {
            dataFormat.stop();
        }
    }

    private SmooksDataFormat newDataFormat(boolean allowExternalEntities) {
        SmooksDataFormat dataFormat = new SmooksDataFormat();
        dataFormat.setSmooksConfig(SMOOKS_CONFIG);
        dataFormat.setAllowExternalEntities(allowExternalEntities);
        dataFormat.setCamelContext(camelContext);
        dataFormat.start();
        return dataFormat;
    }

    private String unmarshalFirstName(SmooksDataFormat dataFormat, String payload) throws Exception {
        UnmarshalProcessor unmarshalProcessor = new UnmarshalProcessor(dataFormat);
        DefaultExchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setBody(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
        unmarshalProcessor.process(exchange);
        Customer customer = exchange.getMessage().getBody(Customer.class);
        return customer.getFirstName();
    }

    private String unmarshalFirstNameSafely(SmooksDataFormat dataFormat, String payload) {
        try {
            String firstName = unmarshalFirstName(dataFormat, payload);
            return firstName == null ? "" : firstName;
        } catch (Exception e) {
            // A parser that rejects the external entity reference is the expected secure behaviour
            return "";
        }
    }

    private String xxePayload() {
        String systemId = secretFile.toUri().toString();
        return "<!DOCTYPE customer [<!ENTITY xxe SYSTEM \"" + systemId + "\">]>\n"
               + "<customer>\n"
               + "  <firstName>&xxe;</firstName>\n"
               + "  <lastName>d</lastName>\n"
               + "  <gender>Male</gender>\n"
               + "  <age>1</age>\n"
               + "  <country>x</country>\n"
               + "</customer>";
    }
}
