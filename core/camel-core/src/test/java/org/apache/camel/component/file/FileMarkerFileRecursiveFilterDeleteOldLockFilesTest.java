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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.Before;
import org.junit.Test;

public class FileMarkerFileRecursiveFilterDeleteOldLockFilesTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myFilter", new MyFileFilter());
        return jndi;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/oldlock");
        super.setUp();
    }

    @Test
    public void testDeleteOldLockOnStartup() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.expectedBodiesReceived("Bye World", "Hi World");
        mock.message(0).header(Exchange.FILE_NAME_ONLY).isEqualTo("bye.txt");
        mock.message(1).header(Exchange.FILE_NAME_ONLY).isEqualTo("hi.txt");
        mock.expectedFileExists("target/data/oldlock/bar/davs.txt");
        mock.expectedFileExists("target/data/oldlock/bar/davs.txt" + FileComponent.DEFAULT_LOCK_FILE_POSTFIX);

        template.sendBodyAndHeader("file:target/data/oldlock", "locked", Exchange.FILE_NAME, "hello.txt" + FileComponent.DEFAULT_LOCK_FILE_POSTFIX);
        template.sendBodyAndHeader("file:target/data/oldlock", "Bye World", Exchange.FILE_NAME, "bye.txt");
        template.sendBodyAndHeader("file:target/data/oldlock/foo", "locked", Exchange.FILE_NAME, "gooday.txt" + FileComponent.DEFAULT_LOCK_FILE_POSTFIX);
        template.sendBodyAndHeader("file:target/data/oldlock/foo", "Hi World", Exchange.FILE_NAME, "hi.txt");
        template.sendBodyAndHeader("file:target/data/oldlock/bar", "locked", Exchange.FILE_NAME, "davs.txt" + FileComponent.DEFAULT_LOCK_FILE_POSTFIX);
        template.sendBodyAndHeader("file:target/data/oldlock/bar", "Davs World", Exchange.FILE_NAME, "davs.txt");

        // start the route
        context.getRouteController().startRoute("foo");

        assertMockEndpointsSatisfied();

        // the lock files should be gone
        assertFileNotExists("target/data/oldlock/hello.txt." + FileComponent.DEFAULT_LOCK_FILE_POSTFIX);
        assertFileNotExists("target/data/oldlock/foo/hegooddayllo.txt." + FileComponent.DEFAULT_LOCK_FILE_POSTFIX);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/data/oldlock?initialDelay=0&delay=10&recursive=true&sortBy=file:name&filter=#myFilter").routeId("foo").noAutoStartup().convertBodyTo(String.class)
                    .to("mock:result");
            }
        };
    }

    private static final class MyFileFilter implements GenericFileFilter<File> {

        @Override
        public boolean accept(GenericFile<File> file) {
            // skip bar directory
            if (file.isDirectory() && "bar".equals(file.getFileName())) {
                return false;
            }
            return true;
        }
    }
}
