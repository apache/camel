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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;

/**
 * Unit tests for {@link AntPathMatcherGenericFileFilter}.
 */
public class AntPathMatcherGenericFileFilterTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/files");
        super.setUp();
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        AntPathMatcherGenericFileFilter<File> filterNotCaseSensitive = new AntPathMatcherGenericFileFilter<File>("**/c*");
        filterNotCaseSensitive.setCaseSensitive(false);

        JndiRegistry jndi = super.createRegistry();
        jndi.bind("filter", new AntPathMatcherGenericFileFilter<File>("**/c*"));
        jndi.bind("caseInsensitiveFilter", filterNotCaseSensitive);
        return jndi;
    }

    public void testInclude() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result1");
        mock.expectedBodiesReceivedInAnyOrder("Hello World");

        template.sendBodyAndHeader("file://target/files/ant-path-1/x/y/z", "Hello World", Exchange.FILE_NAME, "report.txt");
        template.sendBodyAndHeader("file://target/files/ant-path-1/x/y/z", "Hello World 2", Exchange.FILE_NAME, "b.TXT");

        assertMockEndpointsSatisfied();
    }

    public void testExclude() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result2");
        mock.expectedBodiesReceivedInAnyOrder("Hello World 2", "Hello World 3", "Hello World 4");

        template.sendBodyAndHeader("file://target/files/ant-path-2/x/y/z", "Hello World 1", Exchange.FILE_NAME, "report.bak");
        template.sendBodyAndHeader("file://target/files/ant-path-2/x/y/z", "Hello World 2", Exchange.FILE_NAME, "report.txt");
        template.sendBodyAndHeader("file://target/files/ant-path-2/x/y/z", "Hello World 3", Exchange.FILE_NAME, "b.BAK");
        template.sendBodyAndHeader("file://target/files/ant-path-2/x/y/z", "Hello World 4", Exchange.FILE_NAME, "b.TXT");

        assertMockEndpointsSatisfied();
    }

    public void testIncludesAndExcludes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result3");
        mock.expectedBodiesReceivedInAnyOrder("Hello World 2", "Hello World 4");

        template.sendBodyAndHeader("file://target/files/ant-path-3/x/y/z", "Hello World 1", Exchange.FILE_NAME, "a.pdf");
        template.sendBodyAndHeader("file://target/files/ant-path-3/x/y/z", "Hello World 2", Exchange.FILE_NAME, "m.pdf");
        template.sendBodyAndHeader("file://target/files/ant-path-3/x/y/z", "Hello World 3", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader("file://target/files/ant-path-3/x/y/z", "Hello World 4", Exchange.FILE_NAME, "m.txt");
        template.sendBodyAndHeader("file://target/files/ant-path-3/x/y/z", "Hello World 5", Exchange.FILE_NAME, "b.bak");
        template.sendBodyAndHeader("file://target/files/ant-path-3/x/y/z", "Hello World 6", Exchange.FILE_NAME, "m.bak");
        template.sendBodyAndHeader("file://target/files/ant-path-3/x/y/z", "Hello World 7", Exchange.FILE_NAME, "ay.PDF");
        template.sendBodyAndHeader("file://target/files/ant-path-3/x/y/z", "Hello World 8", Exchange.FILE_NAME, "my.Pdf");
        template.sendBodyAndHeader("file://target/files/ant-path-3/x/y/z", "Hello World 9", Exchange.FILE_NAME, "by.TXT");
        template.sendBodyAndHeader("file://target/files/ant-path-3/x/y/z", "Hello World 10", Exchange.FILE_NAME, "my.TxT");
        template.sendBodyAndHeader("file://target/files/ant-path-3/x/y/z", "Hello World 11", Exchange.FILE_NAME, "by.BAK");
        template.sendBodyAndHeader("file://target/files/ant-path-3/x/y/z", "Hello World 12", Exchange.FILE_NAME, "my.BaK");

        assertMockEndpointsSatisfied();
    }

    public void testIncludesAndExcludesAndFilter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result4");
        mock.expectedBodiesReceivedInAnyOrder("Hello World 3");

        template.sendBodyAndHeader("file://target/files/ant-path-4/x/y/z", "Hello World 1", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader("file://target/files/ant-path-4/x/y/z", "Hello World 2", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader("file://target/files/ant-path-4/x/y/z", "Hello World 3", Exchange.FILE_NAME, "c.txt");
        template.sendBodyAndHeader("file://target/files/ant-path-4/x/y/z", "Hello World 4", Exchange.FILE_NAME, "Cy.txt");

        assertMockEndpointsSatisfied();
    }


    public void testIncludeAndAntFilterNotCaseSensitive() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result5");
        mock.expectedBodiesReceivedInAnyOrder("Hello World");

        template.sendBodyAndHeader("file://target/files/ant-path-5/x/y/z", "Hello World", Exchange.FILE_NAME, "report.TXT");

        assertMockEndpointsSatisfied();
    }

    public void testExcludeAndAntFilterNotCaseSensitive() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result6");
        mock.expectedBodiesReceivedInAnyOrder("Hello World 2", "Hello World 4");

        template.sendBodyAndHeader("file://target/files/ant-path-6/x/y/z", "Hello World 1", Exchange.FILE_NAME, "report.bak");
        template.sendBodyAndHeader("file://target/files/ant-path-6/x/y/z", "Hello World 2", Exchange.FILE_NAME, "report.txt");
        template.sendBodyAndHeader("file://target/files/ant-path-6/x/y/z", "Hello World 3", Exchange.FILE_NAME, "b.BAK");
        template.sendBodyAndHeader("file://target/files/ant-path-6/x/y/z", "Hello World 4", Exchange.FILE_NAME, "b.TXT");

        assertMockEndpointsSatisfied();
    }

    public void testIncludesAndExcludesAndAntFilterNotCaseSensitive() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result7");
        mock.expectedBodiesReceivedInAnyOrder("Hello World 2", "Hello World 4", "Hello World 8", "Hello World 10");

        template.sendBodyAndHeader("file://target/files/ant-path-7/x/y/z", "Hello World 1", Exchange.FILE_NAME, "a.pdf");
        template.sendBodyAndHeader("file://target/files/ant-path-7/x/y/z", "Hello World 2", Exchange.FILE_NAME, "m.pdf");
        template.sendBodyAndHeader("file://target/files/ant-path-7/x/y/z", "Hello World 3", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader("file://target/files/ant-path-7/x/y/z", "Hello World 4", Exchange.FILE_NAME, "m.txt");
        template.sendBodyAndHeader("file://target/files/ant-path-7/x/y/z", "Hello World 5", Exchange.FILE_NAME, "b.bak");
        template.sendBodyAndHeader("file://target/files/ant-path-7/x/y/z", "Hello World 6", Exchange.FILE_NAME, "m.bak");
        template.sendBodyAndHeader("file://target/files/ant-path-7/x/y/z", "Hello World 7", Exchange.FILE_NAME, "ay.PDF");
        template.sendBodyAndHeader("file://target/files/ant-path-7/x/y/z", "Hello World 8", Exchange.FILE_NAME, "my.Pdf");
        template.sendBodyAndHeader("file://target/files/ant-path-7/x/y/z", "Hello World 9", Exchange.FILE_NAME, "by.TXT");
        template.sendBodyAndHeader("file://target/files/ant-path-7/x/y/z", "Hello World 10", Exchange.FILE_NAME, "my.TxT");
        template.sendBodyAndHeader("file://target/files/ant-path-7/x/y/z", "Hello World 11", Exchange.FILE_NAME, "By.BAK");
        template.sendBodyAndHeader("file://target/files/ant-path-7/x/y/z", "Hello World 12", Exchange.FILE_NAME, "My.BaK");

        assertMockEndpointsSatisfied();
    }

    public void testIncludesAndExcludesAndFilterAndAntFilterNotCaseSensitive() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result8");
        mock.expectedBodiesReceivedInAnyOrder("Hello World 3", "Hello World 4");

        template.sendBodyAndHeader("file://target/files/ant-path-8/x/y/z", "Hello World 1", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader("file://target/files/ant-path-8/x/y/z", "Hello World 2", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader("file://target/files/ant-path-8/x/y/z", "Hello World 3", Exchange.FILE_NAME, "c.txt");
        template.sendBodyAndHeader("file://target/files/ant-path-8/x/y/z", "Hello World 4", Exchange.FILE_NAME, "Cy.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("file://target/files/ant-path-1?initialDelay=0&delay=10&recursive=true&antInclude=**/*.txt&antFilterCaseSensitive=true")
                    .convertBodyTo(String.class).to("mock:result1");
                from("file://target/files/ant-path-5?initialDelay=0&delay=10&recursive=true&antInclude=**/*.txt&antFilterCaseSensitive=false")
                    .convertBodyTo(String.class).to("mock:result5");

                from("file://target/files/ant-path-2?initialDelay=0&delay=10&recursive=true&antExclude=**/*.bak")
                    .convertBodyTo(String.class).to("mock:result2");
                from("file://target/files/ant-path-6?initialDelay=0&delay=10&recursive=true&antExclude=**/*.bak&antFilterCaseSensitive=false")
                    .convertBodyTo(String.class).to("mock:result6");

                from("file://target/files/ant-path-3?initialDelay=0&delay=10&recursive=true&antInclude=**/*.pdf,**/*.txt&antExclude=**/a*,**/b*")
                    .convertBodyTo(String.class).to("mock:result3");
                from("file://target/files/ant-path-7?initialDelay=0&delay=10&recursive=true&antInclude=**/*.Pdf,**/*.txt&antExclude=**/a*,**/b*&antFilterCaseSensitive=false")
                    .convertBodyTo(String.class).to("mock:result7");

                from("file://target/files/ant-path-4?initialDelay=0&delay=10&recursive=true&antInclude=**/*.txt&antExclude=**/a*&filter=#filter")
                    .convertBodyTo(String.class).to("mock:result4");
                from("file://target/files/ant-path-8?initialDelay=0&delay=10&recursive=true&antInclude=**/*.txt&antExclude=**/a*&filter=#caseInsensitiveFilter")
                    .convertBodyTo(String.class).to("mock:result8");
            }
        };
    }
}
