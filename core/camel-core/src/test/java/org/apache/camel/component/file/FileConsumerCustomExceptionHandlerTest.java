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
package org.apache.camel.component.file;

import java.io.File;
import java.io.IOException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.Registry;
import org.junit.Test;

/**
 *
 */
public class FileConsumerCustomExceptionHandlerTest extends ContextTestSupport {

    private MyReadLockStrategy myReadLockStrategy = new MyReadLockStrategy();
    private MyExceptionHandler myExceptionHandler = new MyExceptionHandler();

    @Test
    public void testCustomExceptionHandler() throws Exception {
        myExceptionHandler.setTemplate(context.createProducerTemplate());

        getMockEndpoint("mock:result").expectedMessageCount(2);
        getMockEndpoint("mock:error").expectedBodiesReceived("Error Forced to simulate no space on device");

        template.sendBodyAndHeader("file:target/data/nospace", "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file:target/data/nospace", "Bye World", Exchange.FILE_NAME, "bye.txt");

        assertMockEndpointsSatisfied();

        assertEquals("Should pickup bye.txt file 2 times", 2, myReadLockStrategy.getCounter());
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myExceptionHandler", myExceptionHandler);
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
                onException(IOException.class).handled(true).log("IOException occurred due: ${exception.message}")
                    // as we handle the exception we can send it to
                    // direct:file-error,
                    // where we could send out alerts or whatever we want
                    .to("direct:file-error");

                // special route that handles file errors
                from("direct:file-error").log("File error route triggered to deal with exception ${exception?.class}")
                    // as this is based on unit test just transform a message
                    // and send it to a mock
                    .transform().simple("Error ${exception.message}").to("mock:error");

                // this is the file route that pickup files, notice how we use
                // our custom exception handler on the consumer
                // the exclusiveReadLockStrategy is only configured because this
                // is from an unit test, so we use that to simulate exceptions
                from("file:target/data/nospace?exclusiveReadLockStrategy=#myReadLockStrategy&exceptionHandler=#myExceptionHandler&initialDelay=0&delay=10")
                    .convertBodyTo(String.class).to("mock:result");
            }
        };
    }
    // END SNIPPET: e2

    // START SNIPPET: e1
    /**
     * Custom {@link ExceptionHandler} to be used on the file consumer, to send
     * exceptions to a Camel route, to let Camel deal with the error.
     */
    private static class MyExceptionHandler implements ExceptionHandler {

        private ProducerTemplate template;

        /**
         * We use a producer template to send a message to the Camel route
         */
        public void setTemplate(ProducerTemplate template) {
            this.template = template;
        }

        @Override
        public void handleException(Throwable exception) {
            handleException(exception.getMessage(), exception);
        }

        @Override
        public void handleException(String message, Throwable exception) {
            handleException(exception.getMessage(), null, exception);
        }

        @Override
        public void handleException(final String message, final Exchange originalExchange, final Throwable exception) {
            // send the message to the special direct:file-error endpoint, which
            // will trigger exception handling
            //
            template.send("direct:file-error", new Processor() {
                @Override
                public void process(Exchange exchange) throws Exception {
                    // set an exception on the message from the start so the
                    // error handling is triggered
                    exchange.setException(exception);
                    exchange.getIn().setBody(message);
                }
            });
        }
    }
    // END SNIPPET: e1

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
                    // force an exception on acquire attempt for the bye.txt
                    // file, on the first attempt
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
