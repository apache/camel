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
package org.apache.camel.component.xslt;

import org.apache.camel.CamelContext;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.TestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * Unit test for CAMEL-457
 */
public class InvalidXsltFileTest extends TestSupport {

    public void testInvalidStylesheet() throws Exception {
        try {
            RouteBuilder builder = createRouteBuilder();
            CamelContext context = new DefaultCamelContext();
            context.addRoutes(builder);
            context.start();

            fail("Should have thrown a ResolveEndpointFailedException due XSL compilation error");
        } catch (ResolveEndpointFailedException e) {
            // expected
        }

    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("seda:a").to("xslt:org/apache/camel/component/xslt/invalid.xsl");
            }
        };
    }
    
}
