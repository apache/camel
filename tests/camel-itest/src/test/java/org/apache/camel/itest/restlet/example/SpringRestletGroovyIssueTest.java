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
package org.apache.camel.itest.restlet.example;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.apache.xbean.spring.context.ClassPathXmlApplicationContext;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;

/**
 * @version 
 */
public class SpringRestletGroovyIssueTest extends CamelSpringTestSupport {

    private static int port = AvailablePortFinder.getNextAvailable(16001);
    private ExecutorService executorService = Executors.newFixedThreadPool(5);

    static {
        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts
        System.setProperty("SpringRestletGroovyIssueTest.port", Integer.toString(port));
    }

    @Override
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        Properties prop = new Properties();
        prop.put("port", port);
        return prop;
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/itest/restlet/example/SpringRestletGroovyIssueTest.xml");
    }

    @Test
    public void testRestletGroovy() throws Exception {
        for (int i = 0; i < 10; i++) {
            final Integer num = i;
            getMockEndpoint("mock:input").expectedMessageCount(10);
            getMockEndpoint("mock:output").expectedBodiesReceivedInAnyOrder("0", "1", "2", "3", "4", "5", "6", "7", "8", "9");

            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    String s = "" + num;
                    Object response = template.requestBody("restlet:http://localhost:" + port + "/foo/" + s + "?restletMethod=GET", "");
                    assertEquals(s, response);
                };
            });
        }

        assertMockEndpointsSatisfied();

        executorService.shutdownNow();
    }

}
