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

import org.apache.camel.CamelExchangeException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * CAMEL-27264: a file exchange that the receive call of the polling consumer cannot return was never started by any
 * route, so its file is aborted, and made available to be polled again. It must not be rolled back, as the rollback
 * moves the file with the moveFailed option.
 */
public class FileNotReturnedByPollingConsumerAbortTest extends ContextTestSupport {

    @Test
    public void testFileIsAbortedAndNotMovedByMoveFailed() throws Exception {
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        FileEndpoint endpoint = context.getEndpoint(fileUri("?moveFailed=error"), FileEndpoint.class);
        FileConsumer consumer = (FileConsumer) endpoint.createConsumer(exchange -> {
        });

        File file = testFile("hello.txt").toFile();
        GenericFile<File> genericFile
                = FileConsumer.asGenericFile(endpoint.getConfiguration().getDirectory(), file, null, false);
        String absoluteFileName = genericFile.getAbsoluteFilePath();

        // the file is polled, so it is in progress, and its completion is registered on the exchange
        Exchange notReturned = endpoint.createExchange(genericFile);
        endpoint.getInProgressRepository().add(absoluteFileName);
        notReturned.getExchangeExtension().addOnCompletion(
                new GenericFileOnCompletion<>(
                        endpoint, consumer.operations, consumer.processStrategy, genericFile, absoluteFileName));
        Exchange returned = endpoint.createExchange();

        GenericFilePollingConsumer.rollbackNotReturned(notReturned, returned);

        assertInstanceOf(CamelExchangeException.class, notReturned.getException());
        assertEquals(false, endpoint.getInProgressRepository().contains(absoluteFileName),
                "the file must not be left in the in-progress repository");
        assertFileExists(testFile("hello.txt"));
        assertFileNotExists(testFile("error/hello.txt"));
    }
}
