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
package org.apache.camel.component.stax;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.stax.model.Product;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.stax.StAXBuilder.stax;

public class IssueWithWrongEncodingTest extends CamelTestSupport {

    private static final String XML_1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<products>\n"
            + "    <product>\n"
            + "        <name>first product ";

    private static final String XML_2 = "</name>\n"
            + "    </product>\n"
            + "    <product>\n"
            + "        <name>second product</name>\n"
            + "    </product>\n"
            + "</products>";

    @Override
    public void setUp() throws Exception {
        deleteDirectory("target/encoding");
        super.setUp();
    }

    @Test
    public void testOkEncoding() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        File file = new File("src/test/resources/products_with_valid_utf8.xml");
        template.sendBodyAndHeader("file:target/encoding", file, Exchange.FILE_NAME, "products_with_valid_utf8.xml");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInvalidEncoding() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedFileExists("target/encoding/error/invalid.xml");

        File file = new File("target/encoding/invalid.xml");
        FileOutputStream fos = new FileOutputStream(file, false);
        fos.write(XML_1.getBytes(StandardCharsets.UTF_8));
        // thai elephant is 4 bytes
        fos.write(0xF0);
        fos.write(0x9F);
        fos.write(0x90);
        // lets force an error by only have 3 bytes
        // fos.write(0x98);
        fos.write(XML_2.getBytes(StandardCharsets.UTF_8));
        fos.close();

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/encoding?moveFailed=error")
                    .split(stax(Product.class))
                    .to("mock:result");
            }
        };
    }
}
