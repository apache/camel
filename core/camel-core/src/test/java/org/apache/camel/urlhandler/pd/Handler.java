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
package org.apache.camel.urlhandler.pd;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.spi.Resource;
import org.apache.camel.support.ResourceResolverSupport;
import org.apache.camel.support.ResourceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to simulate a change of the XSD document. During the first call of the resource a XSD is returned which does
 * not fit to the XML document. In the second call a XSD fitting to the XML document is returned. Used in
 * org.apache.camel.component.validator.ValidatorEndpointClearCachedSchemaTest
 */
public class Handler extends ResourceResolverSupport {
    private static final Logger LOG = LoggerFactory.getLogger(Handler.class);

    // wrong  element name will cause the validation to fail
    private static final String XSD_TEMPLATE_1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                                 "<xsd:schema targetNamespace=\"http://apache.camel.org/test\" " +
                                                 "            xmlns=\"http://apache.camel.org/test\" " +
                                                 "            xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
                                                 "    <xsd:complexType name=\"TestMessage\">" +
                                                 "        <xsd:sequence>" +
                                                 "            <xsd:element name=\"Content\" type=\"xsd:string\" />" +
                                                 "        </xsd:sequence>" +
                                                 "        <xsd:attribute name=\"attr\" type=\"xsd:string\" default=\"xsd1\"/>" +
                                                 "    </xsd:complexType>" +
                                                 "    <xsd:element name=\"TestMessage\" type=\"TestMessage\" />" +
                                                 "</xsd:schema>";

    // correct element name, the validation will be corerct
    private static final String XSD_TEMPLATE_2 = XSD_TEMPLATE_1.replace("\"Content\"", "\"MessageContent\"");

    private final AtomicInteger counter;

    public Handler() {
        super("pd");

        this.counter = new AtomicInteger();
    }

    @Override
    protected Resource createResource(String location, String remaining) {
        return new ResourceSupport("mem", location) {
            @Override
            public boolean exists() {
                return true;
            }

            @Override
            public InputStream getInputStream() throws IOException {
                if (counter.getAndIncrement() == 0) {
                    LOG.info("resolved XSD1");
                    return new ByteArrayInputStream(XSD_TEMPLATE_1.getBytes(StandardCharsets.UTF_8));
                } else {
                    LOG.info("resolved XSD2");

                    return new ByteArrayInputStream(XSD_TEMPLATE_2.getBytes(StandardCharsets.UTF_8));
                }
            }
        };
    }
}
