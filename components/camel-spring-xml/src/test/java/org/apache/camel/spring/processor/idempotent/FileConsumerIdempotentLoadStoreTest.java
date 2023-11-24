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
package org.apache.camel.spring.processor.idempotent;

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileConsumerIdempotentLoadStoreTest extends ContextTestSupport {

    private IdempotentRepository repo;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this,
                "org/apache/camel/spring/processor/idempotent/FileConsumerIdempotentLoadStoreTest.xml");
    }

    @SuppressWarnings("unchecked")
    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        Path file = testFile(".filestore.dat");
        try (Writer w = Files.newBufferedWriter(file)) {
            w.write(testFile("report.txt").toAbsolutePath().toString() + LS);
        }

        // add a file to the repo
        repo = context.getRegistry().lookupByNameAndType("fileStore", IdempotentRepository.class);
    }

    @Test
    public void testIdempotentLoad() throws Exception {
        // send two files (report.txt exists already in idempotent repo)
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "report.txt");
        template.sendBodyAndHeader(fileUri(), "Bye World", Exchange.FILE_NAME, "report2.txt");

        // consume the file the first time
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        assertMockEndpointsSatisfied();
        // wait for the exchange to be done, as it only append to idempotent repo after success
        oneExchangeDone.matchesWaitTime();

        String name = FileUtil.normalizePath(testFile("report.txt").toAbsolutePath().toString());
        assertTrue(repo.contains(name), "Should contain file: " + name);

        String name2 = FileUtil.normalizePath(testFile("report2.txt").toAbsolutePath().toString());
        assertTrue(repo.contains(name2), "Should contain file: " + name2);
    }

}
