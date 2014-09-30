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
package org.apache.camel.processor.onexception;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.camel.model.SplitDefinition;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * This test consists in consuming a file in a main route, splitting it to a
 * second route and throwing an exception during the processing of one of the
 * subexchange resulting from the split. It then checks the exception is
 * correctly thrown in second route and propagated to main route.
 * 
 */
public class OnExceptionInSplitTest {
    public static final class TestException extends Exception {
        private static final long serialVersionUID = -3212416735911895495L;
        
        public TestException(String string) {
            super(string);
        }
        
    }
    
    static boolean exceptionThrownInSecondRoute;
    static boolean exceptionPropagatedToMainRoute;
    
    @Before
    public void init() {
        exceptionThrownInSecondRoute = false;
        exceptionPropagatedToMainRoute = false;
    }
    
    @Test
    public void testWithStopOnExceptionWithoutShareUnitOfWork() throws Exception {
        test(true, false);
    }
    
    @Test
    public void testWithoutStopOnExceptionWithoutShareUnitOfWork() throws Exception {
        test(false, false);
    }
    
    @Test
    public void testWithStopOnExceptionWithShareUnitOfWork() throws Exception {
        test(true, true);
        
    }
    
    @Test
    public void testWithoutStopOnExceptionWithShareUnitOfWork() throws Exception {
        test(false, true);
    }
    
    public void test(final boolean stopOnException, final boolean shareUnitOfWork) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final Path tempDir = Files.createTempDirectory(".camelTmp");
        FileWriter writer = new FileWriter(Files.createTempFile(tempDir, "test", ".tmp").toFile());
        writer.write("a@b@c");
        writer.close();
        Main main = new Main();
        main.addRouteBuilder(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(TestException.class).process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        System.out.println("TestException in main route ! " + exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class));
                        exceptionPropagatedToMainRoute = true;
                        latch.countDown();
                    }
                });
                SplitDefinition splitDef = from("file:///" + tempDir.toString() + "?delay=10000").split().tokenize("@");
                if (stopOnException) {
                    splitDef = splitDef.stopOnException();
                }
                if (shareUnitOfWork) {
                    splitDef = splitDef.shareUnitOfWork();
                }
                splitDef.setHeader(Exchange.FILE_NAME).simple("${body}").to("direct://next").end();
            }
        });
        main.addRouteBuilder(new RouteBuilder() {
            
            @Override
            public void configure() throws Exception {
                onException(TestException.class).process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        System.out.println("TestException in second route ! " + exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class));
                        exceptionThrownInSecondRoute = true;
                    }
                });
                from("direct://next").process(new Processor() {
                    int i = 0;
                    
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        if (2 == ++i)
                            throw new TestException("CRAC!");
                    }
                });
            }
        });
        main.start();
        latch.await(10, TimeUnit.SECONDS);
        if (shareUnitOfWork) {
            Assert.assertFalse("exception thrown", exceptionThrownInSecondRoute);
        } else {
            Assert.assertTrue("exception not thrown", exceptionThrownInSecondRoute);
        }
        Assert.assertTrue("exception not propagated", exceptionPropagatedToMainRoute);
        main.stop();
    }
}
