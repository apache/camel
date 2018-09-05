/**
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
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to simulate a change of the XSD document. During the first call of
 * the resource a XSD is returned which does not fit to the XML document. In
 * the second call a XSD fitting to the XML document is returned.
 * Used in org.apache.camel.component.validator.ValidatorEndpointClearCachedSchemaTest
 */
public class Handler extends URLStreamHandler {
    private static int counter;
    private static final Logger LOG = LoggerFactory.getLogger(Handler.class);

    private final String xsdtemplate1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + //
            "<xsd:schema targetNamespace=\"http://apache.camel.org/test\" xmlns=\"http://apache.camel.org/test\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">"
            + //
            "    <xsd:complexType name=\"TestMessage\">" + //
            "        <xsd:sequence>" + //
            "            <xsd:element name=\"Content\" type=\"xsd:string\" />" + // //
            // wrong
            // element
            // name
            // will
            // cause
            // the
            // validation
            // to
            // fail
            "        </xsd:sequence>" + //
            "        <xsd:attribute name=\"attr\" type=\"xsd:string\" default=\"xsd1\"/>" + //
            "    </xsd:complexType>" + //
            "    <xsd:element name=\"TestMessage\" type=\"TestMessage\" />" + //
            "</xsd:schema>"; //

    private final String xsdtemplate2 = xsdtemplate1.replace("\"Content\"", "\"MessageContent\""); // correct
    // element
    // name
    // -->
    // validation
    // will
    // be
    // correct

    private byte[] xsd1 = xsdtemplate1.getBytes(StandardCharsets.UTF_8);
    private byte[] xsd2 = xsdtemplate2.getBytes(StandardCharsets.UTF_8);

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        if (getCounter() == 0) {
            LOG.info("resolved XSD1");
            incrementCounter();
            return new URLConnection(u) {
                @Override
                public void connect() throws IOException {
                    connected = true;
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    return new ByteArrayInputStream(xsd1);
                }
            };
        } else {
            LOG.info("resolved XSD2");
            incrementCounter();
            return new URLConnection(u) {
                @Override
                public void connect() throws IOException {
                    connected = true;
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    return new ByteArrayInputStream(xsd2);
                }
            };
        }
    }

    public static synchronized void incrementCounter() {
        counter++;
    }

    public static synchronized int getCounter() {
        return counter;
    }
}
