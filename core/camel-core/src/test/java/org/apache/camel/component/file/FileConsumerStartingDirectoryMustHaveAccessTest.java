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
import org.apache.camel.Endpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileConsumerStartingDirectoryMustHaveAccessTest extends ContextTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        File file1 = testDirectory("noAccess", true).toFile();
        Assumptions.assumeTrue(file1.setReadable(false));
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        File file1 = testDirectory("noAccess").toFile();
        file1.setReadable(true);
        super.tearDown();
    }

    @Test
    public void testStartingDirectoryMustHaveAccess() {
        Endpoint endpoint = context.getEndpoint(
                fileUri("noAccess?autoCreate=false&startingDirectoryMustExist=true&startingDirectoryMustHaveAccess=true"));

        IOException e = assertThrows(IOException.class,
                () -> {
                    endpoint.createConsumer(exchange -> {
                        // noop
                    });
                }, "Should have thrown an exception");

        assertTrue(e.getMessage().startsWith("Starting directory permission denied"), e.getMessage());
    }
}
