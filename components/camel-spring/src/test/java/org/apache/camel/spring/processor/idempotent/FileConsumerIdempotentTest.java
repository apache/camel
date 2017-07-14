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
package org.apache.camel.spring.processor.idempotent;

import java.io.File;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.util.FileUtil;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;

public class FileConsumerIdempotentTest extends ContextTestSupport {

    private IdempotentRepository<String> repo;

    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this, "org/apache/camel/spring/processor/idempotent/fileConsumerIdempotentTest.xml");
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/fileidempotent");

        super.setUp();
        repo = context.getRegistry().lookupByNameAndType("fileStore", IdempotentRepository.class);
    }


    public void testIdempotent() throws Exception {
        // send a file
        template.sendBodyAndHeader("file://target/fileidempotent/", "Hello World", Exchange.FILE_NAME, "report.txt");

        // consume the file the first time
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        assertMockEndpointsSatisfied();

        // reset mock and set new expectations
        mock.reset();
        mock.expectedMessageCount(0);
        // sleep to let the consumer try to poll the file
        mock.setResultMinimumWaitTime(50);

        // move file back
        File file = new File("target/fileidempotent/done/report.txt");
        File renamed = new File("target/fileidempotent/report.txt");
        file.renameTo(renamed);

        // should NOT consume the file again, let 2 secs pass to let the consumer try to consume it but it should not
        assertMockEndpointsSatisfied();

        String name = FileUtil.normalizePath(new File("target/fileidempotent/report.txt").getAbsolutePath());
        assertTrue("Should contain file: " + name, repo.contains(name));
    }

}

