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
package org.apache.camel.itest.greeter;

import java.io.File;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.itest.utils.extensions.GreeterServiceExtension;
import org.apache.camel.test.junit5.TestSupport;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@CamelSpringTest
@ContextConfiguration
public class CamelGreeterFileDomTest {
    @RegisterExtension
    public static GreeterServiceExtension greeterServiceExtension
            = GreeterServiceExtension.createExtension("CamelGreeterFileDomTest.port");

    private static final String REQUEST = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                                          + "<soap:Body><greetMe xmlns=\"http://apache.org/hello_world_soap_http/types\">"
                                          + "<requestType>Willem</requestType></greetMe></soap:Body></soap:Envelope>";

    @Autowired
    protected CamelContext camelContext;

    @Test
    void testCamelGreeter() {
        TestSupport.deleteDirectory("target/greeter/response");
        assertNotNull(camelContext);

        ProducerTemplate template = camelContext.createProducerTemplate();
        Object result = template.requestBody("direct:start", REQUEST);
        template.stop();

        assertEquals("Hello Willem", result, "The result is wrong.");

        File file = new File("target/greeter/response/response.txt");
        assertTrue(file.exists(), "File " + file + " should be there.");
    }

}
