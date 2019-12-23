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

import java.io.File;
import java.io.FileOutputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.util.FileUtil;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;

public class FileConsumerIdempotentLoadStoreTest extends ContextTestSupport {

    private IdempotentRepository repo;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this, "org/apache/camel/spring/processor/idempotent/fileConsumerIdempotentTest.xml");
    }

    @SuppressWarnings("unchecked")
    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/fileidempotent");
        createDirectory("target/fileidempotent");

        File file = new File("target/fileidempotent/.filestore.dat");
        FileOutputStream fos = new FileOutputStream(file);

        // insert existing name to the file repo, so we should skip this file
        String name = FileUtil.normalizePath(new File("target/fileidempotent/report.txt").getAbsolutePath());
        fos.write(name.getBytes());
        fos.write(LS.getBytes());

        fos.close();

        super.setUp();

        // add a file to the repo
        repo = context.getRegistry().lookupByNameAndType("fileStore", IdempotentRepository.class);
    }

    @Test
    public void testIdempotentLoad() throws Exception {
        // send two files (report.txt exists already in idempotent repo)
        template.sendBodyAndHeader("file://target/fileidempotent/", "Hello World", Exchange.FILE_NAME, "report.txt");
        template.sendBodyAndHeader("file://target/fileidempotent/", "Bye World", Exchange.FILE_NAME, "report2.txt");

        // consume the file the first time
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        assertMockEndpointsSatisfied();
        // wait for the exchange to be done, as it only append to idempotent repo after success
        oneExchangeDone.matchesMockWaitTime();

        String name = FileUtil.normalizePath(new File("target/fileidempotent/report.txt").getAbsolutePath());
        assertTrue("Should contain file: " + name, repo.contains(name));

        String name2 = FileUtil.normalizePath(new File("target/fileidempotent/report2.txt").getAbsolutePath());
        assertTrue("Should contain file: " + name2, repo.contains(name2));
    }

}

