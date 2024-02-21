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
package org.apache.camel.language;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ConstantLanguageBinaryResourceTest extends ContextTestSupport {

    @Test
    public void testConstantBinaryDefault() throws Exception {
        byte[] data = template.requestBody("direct:default", "", byte[].class);
        // should have X number of bytes
        assertNotNull(data);
        assertEquals(10249, data.length);

        // store the logo
        template.sendBodyAndHeader(fileUri(), data, Exchange.FILE_NAME, "savedlogo.jpeg");
    }

    @Test
    public void testConstantBinaryClasspath() throws Exception {
        byte[] data = template.requestBody("direct:classpath", "", byte[].class);
        // should have X number of bytes
        assertNotNull(data);
        assertEquals(10249, data.length);

        // store the logo
        template.sendBodyAndHeader(fileUri(), data, Exchange.FILE_NAME, "savedlogo.jpeg");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:default").to("language:constant:resource:org/apache/camel/logo.jpeg?binary=true");

                from("direct:classpath").to("language:constant:resource:classpath:org/apache/camel/logo.jpeg?binary=true");
            }
        };
    }
}
