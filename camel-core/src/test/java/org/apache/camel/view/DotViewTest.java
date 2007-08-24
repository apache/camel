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
package org.apache.camel.view;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.XPathBuilder;

import java.io.File;
import static org.apache.camel.builder.xml.XPathBuilder.xpath;

/**
 * @version $Revision: 1.1 $
 */
public class DotViewTest extends ContextTestSupport {
    protected RouteDotGenerator generator = new RouteDotGenerator();

    public void testDotFile() throws Exception {
        new File("target").mkdirs();
        
        generator.setFile("target/Example.dot");
        generator.drawRoutes(context);
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("file:foo/xyz?noop=true").
                    choice().
                      when(xpath("/person/city = 'London'")).to("file:target/messages/uk").
                      otherwise().to("file:target/messages/others");

                from("file:foo/bar?noop=true").
                        filter(header("foo").isEqualTo("bar")).
                        to("file:xyz?noop=true");

                from("file:xyz?noop=true").
                        filter(header("foo").isEqualTo("bar")).
                        recipientList(header("bar")).
                        splitter(XPathBuilder.xpath("/invoice/lineItems")).
                        throttler(3).
                        to("mock:result");
            }
        };
    }

}