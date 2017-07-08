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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;

/**
 * Unit test for the file filter option using directories
 */
public class FileConsumerDirectoryFilterTest extends ContextTestSupport {

    private final String fileUrl = "file://target/directoryfilter/?recursive=true&filter=#myFilter&initialDelay=0&delay=10";
    private final Set<String> names = new TreeSet<String>();

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myFilter", new MyDirectoryFilter<Object>());
        return jndi;
    }

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/directoryfilter");
        super.setUp();
    }

    public void testFilterFilesWithARegularFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("file:target/directoryfilter/skipDir/", "This is a file to be filtered",
                Exchange.FILE_NAME, "skipme.txt");

        template.sendBodyAndHeader("file:target/directoryfilter/skipDir2/", "This is a file to be filtered",
                Exchange.FILE_NAME, "skipme.txt");

        template.sendBodyAndHeader("file:target/directoryfilter/okDir/", "Hello World",
                Exchange.FILE_NAME, "hello.txt");

        mock.assertIsSatisfied();

        // check names
        assertEquals(4, names.size());
        // copy to list so its easier to index
        List<String> list = new ArrayList<String>(names);
        list.sort(null);

        assertEquals("okDir", list.get(0));
        // windows or unix paths
        assertTrue(list.get(0), list.get(1).equals("okDir/hello.txt") || list.get(1).equals("okDir\\hello.txt"));
        assertEquals("skipDir", list.get(2));
        assertEquals("skipDir2", list.get(3));
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUrl).convertBodyTo(String.class).to("mock:result");
            }
        };
    }

    // START SNIPPET: e1
    public class MyDirectoryFilter<T> implements GenericFileFilter<T> {

        public boolean accept(GenericFile<T> file) {
            // remember the name due unit testing (should not be needed in regular use-cases)
            names.add(file.getFileName());
            
            // we dont accept any files within directory starting with skip in the name
            if (file.isDirectory() && file.getFileName().startsWith("skip")) {
                return false;
            }

            return true;
        }

    }
    // END SNIPPET: e1

}