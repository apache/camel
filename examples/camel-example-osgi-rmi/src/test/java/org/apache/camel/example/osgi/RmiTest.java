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
package org.apache.camel.example.osgi;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Unit test the example.
 *
 * @version 
 */
public class RmiTest extends CamelSpringTestSupport {
    
    private static int port;

    @BeforeClass
    public static void setupFreePort() throws Exception {
        // find a free port number, and write that in the custom.properties file
        // which we will use for the unit tests, to avoid port number in use problems
        port = AvailablePortFinder.getNextAvailable();
        String s = "port=" + port;

        File custom = new File("target/custom.properties");
        FileOutputStream fos = new FileOutputStream(custom);
        fos.write(s.getBytes());
        fos.close();
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("META-INF/spring/camelContext.xml");
    }

    @Test
    public void testRmi() throws Exception {
        // Create a new camel context to send the request so we can test the service which is deployed into a container
        CamelContext myContext = new DefaultCamelContext();
        ProducerTemplate myTemplate = myContext.createProducerTemplate();
        myTemplate.start();
        try {
            System.out.println("Calling on port " + port);
            String out = myTemplate.requestBody("rmi://localhost:" + port + "/helloServiceBean", "Camel", String.class);
            assertEquals("Hello Camel", out);
        } finally {
            myTemplate.stop();
            myContext.stop();
        }
    }

}
