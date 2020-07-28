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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the file filter option using directories
 */
public class FileConsumerDirectoryFilterTest extends ContextTestSupport {

    private final String fileUrl
            = "file://target/data/directoryfilter/?recursive=true&filter=#myFilter&initialDelay=0&delay=10";
    private final Set<String> names = new TreeSet<>();

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myFilter", new MyDirectoryFilter<>());
        return jndi;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/data/directoryfilter");
        super.setUp();
    }

    @Test
    public void testFilterFilesWithARegularFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("file:target/data/directoryfilter/skipDir/", "This is a file to be filtered",
                Exchange.FILE_NAME, "skipme.txt");

        template.sendBodyAndHeader("file:target/data/directoryfilter/skipDir2/", "This is a file to be filtered",
                Exchange.FILE_NAME, "skipme.txt");

        template.sendBodyAndHeader("file:target/data/directoryfilter/okDir/", "Hello World", Exchange.FILE_NAME, "hello.txt");

        mock.assertIsSatisfied();

        // check names
        assertEquals(4, names.size());
        // copy to list so its easier to index
        List<String> list = new ArrayList<>(names);
        list.sort(null);

        assertEquals("okDir", list.get(0));
        // windows or unix paths
        assertTrue(list.get(1).equals("okDir/hello.txt") || list.get(1).equals("okDir\\hello.txt"), list.get(0));
        assertEquals("skipDir", list.get(2));
        assertEquals("skipDir2", list.get(3));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUrl).convertBodyTo(String.class).to("mock:result");
            }
        };
    }

    // START SNIPPET: e1
    public class MyDirectoryFilter<T> implements GenericFileFilter<T> {

        @Override
        public boolean accept(GenericFile<T> file) {
            // remember the name due unit testing (should not be needed in
            // regular use-cases)
            names.add(file.getFileName());

            // we dont accept any files within directory starting with skip in
            // the name
            if (file.isDirectory() && file.getFileName().startsWith("skip")) {
                return false;
            }

            return true;
        }

    }
    // END SNIPPET: e1

}
