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
package org.apache.camel.component.xslt;

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.FileUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 *
 */
@Ignore
public class XsltIncludeClasspathDotInDirectoryTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/classes/com.mycompany");
        createDirectory("target/classes/com.mycompany");

        // copy templates to this directory
        FileUtil.copyFile(new File("src/test/resources/org/apache/camel/component/xslt/staff_include_classpath2.xsl"),
                          new File("target/classes/com.mycompany/staff_include_classpath2.xsl"));

        FileUtil.copyFile(new File("src/test/resources/org/apache/camel/component/xslt/staff_template.xsl"), new File("target/classes/com.mycompany/staff_template.xsl"));

        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        deleteDirectory("target/data/classes/com.mycompany");
        super.tearDown();
    }

    @Test
    public void testXsltIncludeClasspath() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        // the include file has the span style so check that its there
        mock.message(0).body().contains("<span style=\"font-size=22px;\">Minnie Mouse</span>");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:src/test/data/?fileName=staff.xml&noop=true&initialDelay=0&delay=10").to("xslt:com.mycompany/staff_include_classpath2.xsl").to("log:foo")
                    .to("mock:result");
            }
        };
    }
}
