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
package org.apache.camel.processor;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.builder.Namespaces;

public class XPathWithNamespaceBuilderFilterAndResultTypeTest extends XPathWithNamespaceBuilderFilterTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                // lets define the namespaces we'll need in our filters
                Namespaces ns = new Namespaces("c", "http://acme.com/cheese").add("xsd", "http://www.w3.org/2001/XMLSchema");

                // now lets create an xpath based Message Filter
                from("direct:start").filter(xpath("/c:person[@name='James']", String.class, ns)).to("mock:result");
                // END SNIPPET: example
            }
        };
    }
}
