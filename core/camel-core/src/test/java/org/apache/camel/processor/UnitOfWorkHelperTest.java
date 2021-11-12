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

package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Message;
import org.apache.camel.component.direct.DirectEndpoint;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileMessage;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCreatedEvent;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UnitOfWorkHelperTest extends ContextTestSupport {

    private static final String FILE_CONTENT = "Lorem ipsum dolor sit amet";

    @EndpointInject(value = "mock:result")
    protected MockEndpoint resultEndpoint;
    private DirectEndpoint fromEndpoint;

    private CustomEventNotifier eventNotifier;
    private int numberOfExchangeCreatedEvents;

    @Test
    void testUoWShouldBeClearedOnJobDone() throws Exception {
        eventNotifier = new CustomEventNotifier();
        context.getManagementStrategy().addEventNotifier(eventNotifier);
        Exchange testExchange = createExchange("testFile");

        template.send("direct:from", testExchange);
        template.send("direct:from", testExchange);

        assertEquals(2, numberOfExchangeCreatedEvents);
    }

    private Exchange createExchange(String fileName) {
        Exchange testExchange = new DefaultExchange(context);

        GenericFile<String> testFile = createFile(fileName);
        Message testMessage = new GenericFileMessage<String>(testExchange, testFile);
        testMessage.setBody(testFile);

        testExchange.setIn(testMessage);
        ExtendedExchange extExchange = testExchange.adapt(ExtendedExchange.class);
        extExchange.setFromEndpoint(fromEndpoint);
        testExchange.setProperty(FileComponent.FILE_EXCHANGE_FILE, testFile);

        return testExchange;
    }

    private GenericFile<String> createFile(final String fileName) {
        GenericFile<String> testFile = new GenericFile<String>();

        testFile.setFile(FILE_CONTENT);
        testFile.setAbsoluteFilePath(fileName);
        testFile.setBody(FILE_CONTENT);

        return testFile;
    }

    public class CustomEventNotifier extends EventNotifierSupport {

        @Override
        public void notify(final CamelEvent event) {
            if (event instanceof ExchangeCreatedEvent) {
                numberOfExchangeCreatedEvents += 1;
            }
        }
    }

}
