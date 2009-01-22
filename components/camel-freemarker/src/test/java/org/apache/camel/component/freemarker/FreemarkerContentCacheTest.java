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
package org.apache.camel.component.freemarker;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test the cache when reloading .ftl files in the classpath
 */
public class FreemarkerContentCacheTest extends ContextTestSupport {

    protected void setUp() throws Exception {
        super.setUp();

        // create a vm file in the classpath as this is the tricky reloading stuff
        template.sendBodyAndHeader("file://target/test-classes/org/apache/camel/component/freemarker?append=false", "Hello ${headers.name}", FileComponent.HEADER_FILE_NAME, "hello.ftl");
    }

    public void testNotCached() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello London");

        template.sendBodyAndHeader("direct:a", "Body", "name", "London");
        mock.assertIsSatisfied();

        // now change content in the file in the classpath and try again
        template.sendBodyAndHeader("file://target/test-classes/org/apache/camel/component/freemarker?append=false", "Bye ${headers.name}", FileComponent.HEADER_FILE_NAME, "hello.ftl");

        mock.reset();
        mock.expectedBodiesReceived("Bye Paris");

        template.sendBodyAndHeader("direct:a", "Body", "name", "Paris");
        mock.assertIsSatisfied();
    }

    public void testCached() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello London");

        template.sendBodyAndHeader("direct:b", "Body", "name", "London");
        mock.assertIsSatisfied();

        // now change content in the file in the classpath and try again
        template.sendBodyAndHeader("file://target/test-classes/org/apache/camel/component/freemarker?append=false", "Bye ${headers.name}", FileComponent.HEADER_FILE_NAME, "hello.ftl");

        mock.reset();
        // we must expected the original filecontent as the cache is enabled, so its Hello and not Bye
        mock.expectedBodiesReceived("Hello Paris");

        template.sendBodyAndHeader("direct:b", "Body", "name", "Paris");
        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:a").to("freemarker://org/apache/camel/component/freemarker/hello.ftl?contentCache=false").to("mock:result");

                from("direct:b").to("freemarker://org/apache/camel/component/freemarker/hello.ftl?contentCache=true").to("mock:result");
            }
        };
    }
}
