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

import java.io.FileNotFoundException;
import java.nio.file.Files;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileConsumerAutoCreateDirectoryTest extends ContextTestSupport {

    @Test
    public void testCreateDirectory() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUri("foo"));
        Consumer consumer = endpoint.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                // noop
            }
        });

        consumer.start();
        consumer.stop();

        // the directory should now exists
        assertTrue(Files.exists(testDirectory("foo")), "Directory should be created");
        assertTrue(Files.isDirectory(testDirectory("foo")), "Directory should be a directory");
    }

    @Test
    public void testCreateAbsoluteDirectory() throws Exception {
        // use current dir as base as absolute path
        String base = testDirectory("foo").toAbsolutePath().toString();

        Endpoint endpoint = context.getEndpoint("file://" + base);
        Consumer consumer = endpoint.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                // noop
            }
        });

        consumer.start();
        consumer.stop();

        // the directory should now exists
        assertTrue(Files.exists(testDirectory("foo")), "Directory should be created");
        assertTrue(Files.isDirectory(testDirectory("foo")), "Directory should be a directory");
    }

    @Test
    public void testDoNotCreateDirectory() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUri("foo?autoCreate=false"));
        Consumer consumer = endpoint.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                // noop
            }
        });

        consumer.start();
        consumer.stop();

        // the directory should NOT exists
        assertFalse(Files.exists(testDirectory("foo")), "Directory should NOT be created");
    }

    @Test
    public void testAutoCreateDirectoryWithDot() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUri("foo.bar?autoCreate=true"));
        Consumer consumer = endpoint.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                // noop
            }
        });

        consumer.start();
        consumer.stop();

        // the directory should exist
        assertTrue(Files.exists(testDirectory("foo.bar")), "Directory should be created");
        assertTrue(Files.isDirectory(testDirectory("foo.bar")), "Directory should be a directory");
    }

    @Test
    public void testStartingDirectoryMustExistDirectory() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUri("foo?autoCreate=false&startingDirectoryMustExist=true"));

        FileNotFoundException e = assertThrows(FileNotFoundException.class,
                () -> {
                    endpoint.createConsumer(exchange -> {
                        // noop
                    });
                }, "Should have thrown an exception");

        assertTrue(e.getMessage().startsWith("Starting directory does not exist"));

        // the directory should NOT exists
        assertFalse(Files.exists(testDirectory("foo")), "Directory should NOT be created");
    }

}
