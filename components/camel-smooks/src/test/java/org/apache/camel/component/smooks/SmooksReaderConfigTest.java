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
import org.apache.camel.dataformat.smooks.SmooksDataFormat;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A {@code <reader>} declared without a class sets handlers, features or parameters of the default XML reader of
 * Smooks, so the {@code allowExternalEntities} option applies as it does to a configuration that declares no reader.
 */
class SmooksReaderConfigTest extends CamelTestSupport {

    private static final String CONFIG = "reader-without-class-smooks-config.xml";
    private static final String ENTITY_TEXT = "Text of the external entity";

    @TempDir
    Path tempDir;

    private String customerXml;

    @BeforeEach
    void writeEntityFile() throws Exception {
        Path entityFile = Files.writeString(tempDir.resolve("first-name.txt"), ENTITY_TEXT);
        customerXml = "<!DOCTYPE customer [<!ENTITY name SYSTEM \"" + entityFile.toUri() + "\">]>"
                      + "<customer><firstName>&name;</firstName><lastName>Smith</lastName></customer>";
    }

    @Test
    void readerConfigWithoutClassFollowsAllowExternalEntities() {
        assertFollowsAllowExternalEntities("direct:customer", "direct:customerWithExternalEntities");
    }

    @Test
    void dataFormatReaderConfigWithoutClassFollowsAllowExternalEntities() {
        assertFollowsAllowExternalEntities("direct:unmarshal", "direct:unmarshalWithExternalEntities");
    }

    private void assertFollowsAllowExternalEntities(String endpoint, String endpointWithExternalEntities) {
        Customer customer = template.requestBody(endpoint, customerXml, Customer.class);
        assertEquals("Smith", customer.getLastName());
        assertEquals("", customer.getFirstName(), "The external entity must not be read by default");

        customer = template.requestBody(endpointWithExternalEntities, customerXml, Customer.class);
        assertEquals(ENTITY_TEXT, customer.getFirstName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:customer").to("smooks://" + CONFIG);
                from("direct:customerWithExternalEntities").to("smooks://" + CONFIG + "?allowExternalEntities=true");
                from("direct:unmarshal").unmarshal(smooksDataFormat(false));
                from("direct:unmarshalWithExternalEntities").unmarshal(smooksDataFormat(true));
            }
        };
    }

    private static SmooksDataFormat smooksDataFormat(boolean allowExternalEntities) {
        SmooksDataFormat dataFormat = new SmooksDataFormat();
        dataFormat.setSmooksConfig("/" + CONFIG);
        dataFormat.setAllowExternalEntities(allowExternalEntities);
        return dataFormat;
    }
}
