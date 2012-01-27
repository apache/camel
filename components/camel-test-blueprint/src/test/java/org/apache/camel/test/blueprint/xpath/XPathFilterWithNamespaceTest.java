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
package org.apache.camel.test.blueprint.xpath;

import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.junit.Test;

public class XPathFilterWithNamespaceTest extends CamelBlueprintTestSupport {

    protected String matchingBody = "<person name='James' city='London' xmlns='http://example.com/person'/>";
    protected String notMatchingBody = "<person name='Hiram' city='Tampa' xmlns='http://example.com/person'/>";

    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/blueprint/xpath/xpathFilterWithNamespaceTest.xml";
    }

    @Test
    public void testSendMatchingMessage() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(matchingBody);

        sendBody("direct:start", matchingBody);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendNotMatchingMessage() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        sendBody("direct:start", notMatchingBody);

        assertMockEndpointsSatisfied();
    }

}
