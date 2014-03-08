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
package org.apache.camel.component.file.remote;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileFilter;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test to verify FTP filter option.
 */
public class FromFtpRemoteFileFilterTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/filefilter?password=admin&filter=#myFilter";
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myFilter", new MyFileFilter<Object>());
        return jndi;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        prepareFtpServer();
    }
    
    @Test
    public void testFtpFilter() throws Exception {
        if (isPlatform("aix")) {
            // skip testing on AIX as it have an issue with this test with the file filter
            return;
        }

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.expectedBodiesReceivedInAnyOrder("Report 1", "Report 2");
        
        mock.assertIsSatisfied();
    }

    private void prepareFtpServer() throws Exception {
        // prepares the FTP Server by creating files on the server that we want to unit
        // test that we can pool        
        sendFile(getFtpUrl(), "Hello World", "hello.txt");
        sendFile(getFtpUrl(), "Report 1", "report1.txt");
        sendFile(getFtpUrl(), "Bye World", "bye.txt");
        sendFile(getFtpUrl(), "Report 2", "report2.txt");
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(getFtpUrl()).convertBodyTo(String.class).to("mock:result");
            }
        };
    }

    // START SNIPPET: e1
    public class MyFileFilter<T> implements GenericFileFilter<T> {

        public boolean accept(GenericFile<T> file) {
            // we only want report files 
            return file.getFileName().startsWith("report");
        }
    }
    // END SNIPPET: e1
}