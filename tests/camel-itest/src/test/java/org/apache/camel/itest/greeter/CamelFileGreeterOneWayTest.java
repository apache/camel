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

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.itest.utils.extensions.GreeterServiceExtension;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class CamelFileGreeterOneWayTest extends CamelSpringTestSupport {
    @RegisterExtension
    public static GreeterServiceExtension greeterServiceExtension
            = GreeterServiceExtension.createExtension("CamelFileGreeterOneWayTest.port");

    @Test
    void testFileWithOnewayOperation() throws Exception {
        deleteDirectory("target/messages/input/");
        greeterServiceExtension.getGreeter().resetOneWayCounter();
        ProducerTemplate template = context.createProducerTemplate();
        template.sendBodyAndHeader("file://target/messages/input/", "Hello World", Exchange.FILE_NAME, "hello.txt");

        // Sleep a while and wait for the message whole processing
        Thread.sleep(4000);
        template.stop();

        // make sure the greeter is called
        assertEquals(1, greeterServiceExtension.getGreeter().getOneWayCounter(),
                "The oneway operation of greeter should be called");

        File file = new File("target/messages/input/hello.txt");
        assertFalse(file.exists(), "File " + file + " should be deleted");
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/itest/greeter/CamelFileGreeterOneWayTest.xml");
    }

}
