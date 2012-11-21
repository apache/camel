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
import java.io.FileNotFoundException;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * @version 
 */
public class FileConsumerAutoCreateDirectoryTest extends ContextTestSupport {

    public void testCreateDirectory() throws Exception {
        deleteDirectory("target/file/foo");

        Endpoint endpoint = context.getEndpoint("file://target/file/foo");
        Consumer consumer = endpoint.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                // noop
            }
        });

        consumer.start();
        consumer.stop();

        // the directory should now exists
        File dir = new File("target/file/foo");
        assertTrue("Directory should be created", dir.exists());
        assertTrue("Directory should be a directory", dir.isDirectory());
    }

    public void testCreateAbsoluteDirectory() throws Exception {
        deleteDirectory("target/file/foo");
        // use current dir as base as absolute path
        String base = new File("").getAbsolutePath() + "/target/file/foo";

        Endpoint endpoint = context.getEndpoint("file://" + base);
        Consumer consumer = endpoint.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                // noop
            }
        });

        consumer.start();
        consumer.stop();

        // the directory should now exists
        File dir = new File(base);
        assertTrue("Directory should be created", dir.exists());
        assertTrue("Directory should be a directory", dir.isDirectory());
    }

    public void testDoNotCreateDirectory() throws Exception {
        deleteDirectory("target/file/foo");

        Endpoint endpoint = context.getEndpoint("file://target/file/foo?autoCreate=false");
        Consumer consumer = endpoint.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                // noop
            }
        });

        consumer.start();
        consumer.stop();

        // the directory should NOT exists
        File dir = new File("target/file/foo");
        assertFalse("Directory should NOT be created", dir.exists());
    }

    public void testAutoCreateDirectoryWithDot() throws Exception {
        deleteDirectory("target/file/foo.bar");

        Endpoint endpoint = context.getEndpoint("file://target/file/foo.bar?autoCreate=true");
        Consumer consumer = endpoint.createConsumer(new Processor() {
            public void process(Exchange exchange) throws Exception {
                // noop
            }
        });

        consumer.start();
        consumer.stop();

        // the directory should exist
        File dir = new File("target/file/foo.bar");
        assertTrue("Directory should be created", dir.exists());
        assertTrue("Directory should be a directory", dir.isDirectory());
    }

    public void testStartingDirectoryMustExistDirectory() throws Exception {
        deleteDirectory("target/file/foo");

        Endpoint endpoint = context.getEndpoint("file://target/file/foo?autoCreate=false&startingDirectoryMustExist=true");
        try {
            endpoint.createConsumer(new Processor() {
                public void process(Exchange exchange) throws Exception {
                    // noop
                }
            });
            fail("Should have thrown an exception");
        } catch (FileNotFoundException e) {
            assertTrue(e.getMessage().startsWith("Starting directory does not exist"));
        }

        // the directory should NOT exists
        File dir = new File("target/file/foo");
        assertFalse("Directory should NOT be created", dir.exists());
    }


}
