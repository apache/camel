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

package org.apache.camel.component.cxf;

import org.w3c.dom.Document;

import org.apache.camel.builder.RouteBuilder;

public class CxfPayloadProducerNamespaceOnEnvelopeStreamCacheTest extends CxfPayloadProducerNamespaceOnEnvelopeTest {
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:router") //
                        .streamCaching()
                        // call an external Web service in payload mode
                        .to("cxf:bean:serviceEndpoint?dataFormat=PAYLOAD")
                        // Check that the issue doesn't occur if stream caching is enabled
                        // Parse to DOM to make sure it's still valid XML
                        .convertBodyTo(Document.class)
                        // Convert back to String to make testing the result
                        // easier
                        .convertBodyTo(String.class);
                // This route just returns the test message
                from("cxf:bean:serviceEndpoint?dataFormat=MESSAGE").setBody().constant(RESPONSE_MESSAGE);
            }
        };
    }
}
