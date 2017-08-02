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
import java.io.IOException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;

/**
 *
 */
public class FileConsumerBridgeRouteExceptionHandlerTest extends ContextTestSupport {

    private MyReadLockStrategy myReadLockStrategy = new MyReadLockStrategy();

    public void testCustomExceptionHandler() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);
        getMockEndpoint("mock:error").expectedBodiesReceived("Error Forced to simulate no space on device");

        template.sendBodyAndHeader("file:target/nospace", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file:target/nospace", "Bye World", Exchange.FILE_NAME, "bye.txt");

        assertMockEndpointsSatisfied();

        assertEquals("Should pickup bye.txt file 2 times", 2, myReadLockStrategy.getCounter());
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myReadLockStrategy", myReadLockStrategy);
        return jndi;
    }

    // START SNIPPET: e2
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // to handle any IOException being thrown
                onException(IOException.class)
                    .handled(true)
                    .log("IOException occurred due: ${exception.message}")
                    .transform().simple("Error ${exception.message}")
                    .to("mock:error");

                // this is the file route that pickup files, notice how we bridge the consumer to use the Camel routing error handler
                // the exclusiveReadLockStrategy is only configured because this is from an unit test, so we use that to simulate exceptions
                from("file:target/nospace?exclusiveReadLockStrategy=#myReadLockStrategy&consumer.bridgeErrorHandler=true&initialDelay=0&delay=10")
                    .convertBodyTo(String.class)
                    .to("mock:result");
            }
        };
    }
    // END SNIPPET: e2

    // used for simulating exception during acquiring a lock on the file
    private static class MyReadLockStrategy implements GenericFileExclusiveReadLockStrategy<File> {

        private int counter;

        @Override
        public void prepareOnStartup(GenericFileOperations<File> operations, GenericFileEndpoint<File> endpoint) throws Exception {
            // noop
        }

        @Override
        public boolean acquireExclusiveReadLock(GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange) throws Exception {
            if (file.getFileNameOnly().equals("bye.txt")) {
                if (counter++ == 0) {
                    // force an exception on acquire attempt for the bye.txt file, on the first attempt
                    throw new IOException("Forced to simulate no space on device");
                }
            }

            return true;
        }

        @Override
        public void releaseExclusiveReadLockOnAbort(GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange) throws Exception {
            // noop
        }

        @Override
        public void releaseExclusiveReadLockOnRollback(GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange) throws Exception {
            // noop
        }

        @Override
        public void releaseExclusiveReadLockOnCommit(GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange) throws Exception {
            // noop
        }

        @Override
        public void setTimeout(long timeout) {
            // noop
        }

        @Override
        public void setCheckInterval(long checkInterval) {
            // noop
        }

        @Override
        public void setReadLockLoggingLevel(LoggingLevel readLockLoggingLevel) {
            // noop
        }

        @Override
        public void setMarkerFiler(boolean markerFile) {
            // noop
        }

        @Override
        public void setDeleteOrphanLockFiles(boolean deleteOrphanLockFiles) {
            // noop
        }

        public int getCounter() {
            return counter;
        }
    }
}
