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
package org.apache.camel.processor.idempotent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

import static org.apache.camel.processor.idempotent.FileIdempotentRepository.fileIdempotentRepository;

public class FileIdempotentRepositoryTest extends AbstractIdempotentRepositoryTest<FileIdempotentRepository> {

    public FileIdempotentRepositoryTest() throws IOException {
        super(createRepo());
    }

    private static FileIdempotentRepository createRepo() throws IOException {
        Path file = Files.createTempFile(Paths.get("target"), FileIdempotentRepositoryTest.class.getSimpleName(), "dat");
        return (FileIdempotentRepository) fileIdempotentRepository(file.toFile());
    }

    @Test
    public void testLoadsPrevEntries() throws Exception {
        // Given:
        Files.write(repo.getFileStore().toPath(), "a\nb\n".getBytes()); // Should use DSL, not knowledge of the file format
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").idempotentConsumer(body(), repo).to("mock:result");
            }
        });
        startCamelContext();
        getMockEndpoint("mock:result").expectedBodiesReceived("c");
        // When:
        sendBodies("direct:start", "a", "b", "a", "b", "c");
        // Then:
        assertMockEndpointsSatisfied();
    }

}