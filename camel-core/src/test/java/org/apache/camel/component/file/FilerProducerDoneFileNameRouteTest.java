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
package org.apache.camel.component.file;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.impl.JndiRegistry;

/**
 * Unit test for writing done files
 */
public class FilerProducerDoneFileNameRouteTest extends ContextTestSupport {

    private Properties myProp = new Properties();

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/done");
        super.setUp();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myProp", myProp);
        return jndi;
    }

    public void testProducerPlaceholderPrefixDoneFileName() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();

        assertTrue(oneExchangeDone.matches(5, TimeUnit.SECONDS));

        File file = new File("target/done/hello.txt");
        assertEquals("File should exists", true, file.exists());

        File done = new File("target/done/done-hello.txt");
        assertEquals("Done file should exists", true, done.exists());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                myProp.put("myDir", "target/done");

                PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
                pc.setLocation("ref:myProp");

                from("direct:start")
                    .to("file:{{myDir}}?doneFileName=done-${file:name}")
                    .to("mock:result");
            }
        };
    }
}
