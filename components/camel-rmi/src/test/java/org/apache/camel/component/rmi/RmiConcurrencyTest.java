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
package org.apache.camel.component.rmi;

import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.registry.LocateRegistry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class RmiConcurrencyTest extends CamelTestSupport {

    private static boolean created;

    protected int getPort() {
        return 37543;
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        if (classPathHasSpaces()) {
            return null;
        }

        if (!created) {
            LocateRegistry.createRegistry(getPort());
            created = true;
        }

        JndiRegistry context = super.createRegistry();
        context.bind("echo", new EchoService());
        return context;
    }

    @Test
    public void testNoConcurrentProducers() throws Exception {
        doSendMessages(1, 1);
    }

    @Test
    public void testConcurrentProducers() throws Exception {
        doSendMessages(10, 5);
    }

    private void doSendMessages(int files, int poolSize) throws Exception {
        if (classPathHasSpaces()) {
            return;
        }

        getMockEndpoint("mock:result").expectedMessageCount(files);
        getMockEndpoint("mock:result").assertNoDuplicates(body());
        getMockEndpoint("mock:result").message(0).body().contains("Echo");

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        for (int i = 0; i < files; i++) {
            final int index = i;
            executor.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    template.sendBody("direct:echo", "" + index);
                    return null;
                }
            });
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // setup the jmi server endpoint
                RmiEndpoint echo = (RmiEndpoint)endpoint("rmi://localhost:37543/echo");
                echo.setRemoteInterfaces(IEcho.class);
                from(echo).to("bean:echo");

                // and our route where we call the server
                from("direct:echo").to("rmi://localhost:37543/echo").to("mock:result");
            }
        };
    }

    private boolean classPathHasSpaces() {
        ClassLoader cl = getClass().getClassLoader();
        if (cl instanceof URLClassLoader) {
            URLClassLoader ucl = (URLClassLoader)cl;
            URL[] urls = ucl.getURLs();
            for (int i = 0; i < urls.length; i++) {
                if (urls[i].getPath().contains(" ")) {
                    System.err.println("=======================================================================");
                    System.err.println(" TEST Skipped: " + this.getClass().getName());
                    System.err.println("   Your probably on windows.  We detected that the classpath");
                    System.err.println("   has a space in it.  Try running maven with the following option: ");
                    System.err.println("   -Dmaven.repo.local=C:\\DOCUME~1\\userid\\.m2\\repository");
                    System.err.println("=======================================================================");
                    return true;
                }
            }
        }
        return false;
    }
}