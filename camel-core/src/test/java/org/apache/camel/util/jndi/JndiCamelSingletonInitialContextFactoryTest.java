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
package org.apache.camel.util.jndi;

import java.io.File;
import java.io.FileOutputStream;
import javax.naming.Context;
import javax.naming.InitialContext;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.util.FileUtil;

/**
 *
 */
public class JndiCamelSingletonInitialContextFactoryTest extends ContextTestSupport {

    private static final String FAKE = "!!! Get DataSource fake !!!";
    private File file = new File("src/test/resources/jndi.properties");

    @Override
    protected void setUp() throws Exception {
        FileUtil.deleteFile(file);

        // crete jndi.properties file
        FileOutputStream fos = new FileOutputStream(file);
        try {
            String name = "java.naming.factory.initial=" + CamelSingletonInitialContextFactory.class.getName();
            fos.write(name.getBytes());
        } finally {
            fos.close();
        }

        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        FileUtil.deleteFile(file);
        super.tearDown();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        // create jndi registry to use in Camel, using the default initial contex (which will read jndi.properties)
        // (instead of what test-support offers normally)
        JndiRegistry jndi = new JndiRegistry(new InitialContext());
        jndi.bind("jdbc/myDataSource", FAKE);
        return jndi;
    }

    public void testSingletonJndiContext() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(FAKE);

        template.sendBody("direct:simple", "Dummy");

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:simple")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                // calling this should get us the existing context
                                Context context = new InitialContext();
                                exchange.getIn().setBody(context.lookup("jdbc/myDataSource").toString());
                            }
                        })
                        .to("mock:result");
            }
        };
    }
}
