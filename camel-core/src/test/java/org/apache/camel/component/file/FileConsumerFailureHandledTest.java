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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for consuming files but the exchange fails and is handled
 * by the failure handler (usually the DeadLetterChannel)
 */
public class FileConsumerFailureHandledTest extends ContextTestSupport {

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        deleteDirectory("target/messages");
    }

    public void testParis() throws Exception {
        deleteDirectory("target/messages");

        MockEndpoint mock = getMockEndpoint("mock:valid");
        mock.expectedBodiesReceived("Hello Paris");

        template.sendBodyAndHeader("file:target/messages/input/?delete=true", "Paris", FileComponent.HEADER_FILE_NAME, "paris.txt");
        mock.assertIsSatisfied();

        // sleep otherwise the file assertions below could fail
        Thread.sleep(500);

        asserFiles("paris.txt");
    }

    public void testLondon() throws Exception {
        deleteDirectory("target/messages");

        MockEndpoint mock = getMockEndpoint("mock:invalid");
        // we get the original input so its not Hello London but only London
        mock.expectedBodiesReceived("London");

        template.sendBodyAndHeader("file:target/messages/input/?delete=true", "London", FileComponent.HEADER_FILE_NAME, "london.txt");
        mock.assertIsSatisfied();

        // sleep otherwise the file assertions below could fail
        Thread.sleep(500);

        asserFiles("london.txt");
    }
    
    public void testMadrid() throws Exception {
        deleteDirectory("target/messages");

        MockEndpoint mock = getMockEndpoint("mock:error");
        // we get the original input so its not Hello London but only London
        mock.expectedBodiesReceived("Madrid");

        template.sendBodyAndHeader("file:target/messages/input/?delete=true", "Madrid", FileComponent.HEADER_FILE_NAME, "madrid.txt");
        mock.assertIsSatisfied();

        // sleep otherwise the file assertions below could fail
        Thread.sleep(500);

        asserFiles("madrid.txt");
    }

    private static void asserFiles(String filename) {
        // file should be deleted as deleted=true in parameter in the route below
        File file = new File("target/messages/input/" + filename);
        assertEquals("File " + filename + " should be deleted", false, file.exists());

        // and no lock files
        file = new File("target/messages/input/" + filename + FileEndpoint.DEFAULT_LOCK_FILE_POSTFIX);
        assertEquals("File " + filename + " lock should be deleted", false, file.exists());
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // make sure mock:error is the dead letter channel
                errorHandler(deadLetterChannel("mock:error").maximumRedeliveries(2));

                // special failure handler for ValidationException
                exception(ValidationException.class).to("mock:invalid");

                // our route logic to process files from the input folder
                from("file:target/messages/input/?delete=true").
                    process(new MyValidatorProcessor()).
                    to("mock:valid");
            }
        };
    }

    private class MyValidatorProcessor implements Processor {
        public void process(Exchange exchange) throws Exception {
            String body = exchange.getIn().getBody(String.class);
            if ("London".equals(body)) {
                throw new ValidationException(exchange, "Forced exception by unit test");
            } else if ("Madrid".equals(body)) {
                throw new RuntimeCamelException("Madrid is not a supported city");
            }
            exchange.getOut().setBody("Hello " + body);
        }
    }
    
}
