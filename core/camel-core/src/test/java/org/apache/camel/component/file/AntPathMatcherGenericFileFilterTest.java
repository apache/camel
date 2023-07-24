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
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AntPathMatcherGenericFileFilter}.
 */
public class AntPathMatcherGenericFileFilterTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected Registry createRegistry() throws Exception {
        AntPathMatcherGenericFileFilter<File> filterNotCaseSensitive = new AntPathMatcherGenericFileFilter<>("**/c*");
        filterNotCaseSensitive.setCaseSensitive(false);

        Registry jndi = super.createRegistry();
        jndi.bind("filter", new AntPathMatcherGenericFileFilter<File>("**/c*"));
        jndi.bind("caseInsensitiveFilter", filterNotCaseSensitive);
        return jndi;
    }

    @Test
    public void testInclude() throws Exception {
        context.setAutoStartup(false);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri(
                        "files/ant-path-1?initialDelay=0&delay=10&recursive=true&antInclude=**/*.txt&antFilterCaseSensitive=true"))
                        .convertBodyTo(String.class)
                        .to("mock:result1");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result1");
        mock.expectedBodiesReceivedInAnyOrder("Hello World");

        String endpointUri = fileUri("files/ant-path-1/x/y/z");
        template.sendBodyAndHeader(endpointUri, "Hello World", Exchange.FILE_NAME, "report.txt");
        template.sendBodyAndHeader(endpointUri, "Hello World 2", Exchange.FILE_NAME, "b.TXT");

        context.getRouteController().startAllRoutes();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testExclude() throws Exception {
        context.setAutoStartup(false);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri("files/ant-path-2?initialDelay=0&delay=10&recursive=true&antExclude=**/*.bak"))
                        .convertBodyTo(String.class).to("mock:result2");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result2");
        mock.expectedBodiesReceivedInAnyOrder("Hello World 2", "Hello World 3", "Hello World 4");

        String endpointUri = fileUri("files/ant-path-2/x/y/z");
        template.sendBodyAndHeader(endpointUri, "Hello World 1", Exchange.FILE_NAME, "report.bak");
        template.sendBodyAndHeader(endpointUri, "Hello World 2", Exchange.FILE_NAME, "report.txt");
        template.sendBodyAndHeader(endpointUri, "Hello World 3", Exchange.FILE_NAME, "b.BAK");
        template.sendBodyAndHeader(endpointUri, "Hello World 4", Exchange.FILE_NAME, "b.TXT");

        context.getRouteController().startAllRoutes();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testIncludesAndExcludes() throws Exception {
        context.setAutoStartup(false);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri(
                        "files/ant-path-3?initialDelay=0&delay=10&recursive=true&antInclude=**/*.pdf,**/*.txt&antExclude=**/a*,**/b*"))
                        .convertBodyTo(String.class)
                        .to("mock:result3");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result3");
        mock.expectedBodiesReceivedInAnyOrder("Hello World 2", "Hello World 4");

        String endpointUri = fileUri("files/ant-path-3/x/y/z");
        template.sendBodyAndHeader(endpointUri, "Hello World 1", Exchange.FILE_NAME, "a.pdf");
        template.sendBodyAndHeader(endpointUri, "Hello World 2", Exchange.FILE_NAME, "m.pdf");
        template.sendBodyAndHeader(endpointUri, "Hello World 3", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader(endpointUri, "Hello World 4", Exchange.FILE_NAME, "m.txt");
        template.sendBodyAndHeader(endpointUri, "Hello World 5", Exchange.FILE_NAME, "b.bak");
        template.sendBodyAndHeader(endpointUri, "Hello World 6", Exchange.FILE_NAME, "m.bak");
        template.sendBodyAndHeader(endpointUri, "Hello World 7", Exchange.FILE_NAME, "ay.PDF");
        template.sendBodyAndHeader(endpointUri, "Hello World 8", Exchange.FILE_NAME, "my.Pdf");
        template.sendBodyAndHeader(endpointUri, "Hello World 9", Exchange.FILE_NAME, "by.TXT");
        template.sendBodyAndHeader(endpointUri, "Hello World 10", Exchange.FILE_NAME, "my.TxT");
        template.sendBodyAndHeader(endpointUri, "Hello World 11", Exchange.FILE_NAME, "by.BAK");
        template.sendBodyAndHeader(endpointUri, "Hello World 12", Exchange.FILE_NAME, "my.BaK");

        context.getRouteController().startAllRoutes();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testIncludesAndExcludesAndFilter() throws Exception {
        context.setAutoStartup(false);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri(
                        "files/ant-path-4?initialDelay=0&delay=10&recursive=true&antInclude=**/*.txt&antExclude=**/a*&filter=#filter"))
                        .convertBodyTo(String.class)
                        .to("mock:result4");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result4");
        mock.expectedBodiesReceivedInAnyOrder("Hello World 3");

        String endpointUri = fileUri("files/ant-path-4/x/y/z");
        template.sendBodyAndHeader(endpointUri, "Hello World 1", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader(endpointUri, "Hello World 2", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader(endpointUri, "Hello World 3", Exchange.FILE_NAME, "c.txt");
        template.sendBodyAndHeader(endpointUri, "Hello World 4", Exchange.FILE_NAME, "Cy.txt");

        context.getRouteController().startAllRoutes();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testIncludeAndAntFilterNotCaseSensitive() throws Exception {
        context.setAutoStartup(false);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri(
                        "files/ant-path-5?initialDelay=0&delay=10&recursive=true&antInclude=**/*.txt&antFilterCaseSensitive=false"))
                        .convertBodyTo(String.class)
                        .to("mock:result5");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result5");
        mock.expectedBodiesReceivedInAnyOrder("Hello World");

        String endpointUri = fileUri("files/ant-path-5/x/y/z");
        template.sendBodyAndHeader(endpointUri, "Hello World", Exchange.FILE_NAME,
                "report.TXT");

        context.getRouteController().startAllRoutes();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testExcludeAndAntFilterNotCaseSensitive() throws Exception {
        context.setAutoStartup(false);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri(
                        "files/ant-path-6?initialDelay=0&delay=10&recursive=true&antExclude=**/*.bak&antFilterCaseSensitive=false"))
                        .convertBodyTo(String.class)
                        .to("mock:result6");

            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result6");
        mock.expectedBodiesReceivedInAnyOrder("Hello World 2", "Hello World 4");

        String endpointUri = fileUri("files/ant-path-6/x/y/z");
        template.sendBodyAndHeader(endpointUri, "Hello World 1", Exchange.FILE_NAME, "report.bak");
        template.sendBodyAndHeader(endpointUri, "Hello World 2", Exchange.FILE_NAME, "report.txt");
        template.sendBodyAndHeader(endpointUri, "Hello World 3", Exchange.FILE_NAME, "b.BAK");
        template.sendBodyAndHeader(endpointUri, "Hello World 4", Exchange.FILE_NAME, "b.TXT");

        context.getRouteController().startAllRoutes();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testIncludesAndExcludesAndAntFilterNotCaseSensitive() throws Exception {
        context.setAutoStartup(false);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri(
                        "files/ant-path-7?initialDelay=0&delay=10&recursive=true&antInclude=**/*.Pdf,**/*.txt&antExclude=**/a*,**/b*&antFilterCaseSensitive=false"))
                        .convertBodyTo(String.class).to("mock:result7");

            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result7");
        mock.expectedBodiesReceivedInAnyOrder("Hello World 2", "Hello World 4", "Hello World 8", "Hello World 10");

        String endpointUri = fileUri("files/ant-path-7/x/y/z");
        template.sendBodyAndHeader(endpointUri, "Hello World 1", Exchange.FILE_NAME, "a.pdf");
        template.sendBodyAndHeader(endpointUri, "Hello World 2", Exchange.FILE_NAME, "m.pdf");
        template.sendBodyAndHeader(endpointUri, "Hello World 3", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader(endpointUri, "Hello World 4", Exchange.FILE_NAME, "m.txt");
        template.sendBodyAndHeader(endpointUri, "Hello World 5", Exchange.FILE_NAME, "b.bak");
        template.sendBodyAndHeader(endpointUri, "Hello World 6", Exchange.FILE_NAME, "m.bak");
        template.sendBodyAndHeader(endpointUri, "Hello World 7", Exchange.FILE_NAME, "ay.PDF");
        template.sendBodyAndHeader(endpointUri, "Hello World 8", Exchange.FILE_NAME, "my.Pdf");
        template.sendBodyAndHeader(endpointUri, "Hello World 9", Exchange.FILE_NAME, "by.TXT");
        template.sendBodyAndHeader(endpointUri, "Hello World 10", Exchange.FILE_NAME, "my.TxT");
        template.sendBodyAndHeader(endpointUri, "Hello World 11", Exchange.FILE_NAME, "By.BAK");
        template.sendBodyAndHeader(endpointUri, "Hello World 12", Exchange.FILE_NAME, "My.BaK");

        context.getRouteController().startAllRoutes();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testIncludesAndExcludesAndFilterAndAntFilterNotCaseSensitive() throws Exception {
        context.setAutoStartup(false);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(fileUri(
                        "files/ant-path-8?initialDelay=0&delay=10&recursive=true&antInclude=**/*.txt&antExclude=**/a*&filter=#caseInsensitiveFilter"))
                        .convertBodyTo(String.class).to("mock:result8");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result8");
        mock.expectedBodiesReceivedInAnyOrder("Hello World 3", "Hello World 4");

        String endpointUri = fileUri("files/ant-path-8/x/y/z");
        template.sendBodyAndHeader(endpointUri, "Hello World 1", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader(endpointUri, "Hello World 2", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader(endpointUri, "Hello World 3", Exchange.FILE_NAME, "c.txt");
        template.sendBodyAndHeader(endpointUri, "Hello World 4", Exchange.FILE_NAME, "Cy.txt");

        context.getRouteController().startAllRoutes();

        assertMockEndpointsSatisfied();
    }

}
