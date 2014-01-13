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
package org.apache.camel.dataformat.xmljson;

import java.io.InputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JsonToXmlAttributesTest extends CamelTestSupport {

    @Test
    public void shouldCreateAttribute() {
        // Given
        InputStream inStream = getClass().getClassLoader().getResourceAsStream("org/apache/camel/dataformat/xmljson/jsonToXmlAttributesMessage.json");
        String in = context.getTypeConverter().convertTo(String.class, inStream);

        // When
        String xml = template.requestBody("direct:unmarshal", in, String.class);

        // Then
        assertTrue(xml.contains(" b=\"2\""));
    }

    @Test
    public void shouldCreateOnlyOneAttribute() {
        // Given
        InputStream inStream = getClass().getClassLoader().getResourceAsStream("org/apache/camel/dataformat/xmljson/jsonToXmlAttributesMessage.json");
        String in = context.getTypeConverter().convertTo(String.class, inStream);

        // When
        String xml = template.requestBody("direct:unmarshal", in, String.class);

        // Then
        assertFalse(xml.contains("a="));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:unmarshal").unmarshal().xmljson().to("mock:xml");
            }
        };
    }

}
