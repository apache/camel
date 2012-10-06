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

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.junit.Test;

public class XPathHeaderNameTest extends CamelBlueprintTestSupport {

    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/blueprint/xpath/XPathHeaderNameTest.xml";
    }

    @Test
    public void testChoiceWithHeaderNamePremium() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:premium");
        mock.expectedBodiesReceived("<response>OK</response>");
        mock.expectedHeaderReceived("invoiceDetails", "<invoice orderType='premium'><person><name>Alan</name></person></invoice>");
 
        template.sendBodyAndHeader("direct:in", "<response>OK</response>",
                                   "invoiceDetails", "<invoice orderType='premium'><person><name>Alan</name></person></invoice>");

        mock.assertIsSatisfied();
    }

    @Test
    public void testChoiceWithHeaderNameStandard() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:standard");
        mock.expectedBodiesReceived("<response>OK</response>");
        mock.expectedHeaderReceived("invoiceDetails", "<invoice orderType='standard'><person><name>Alan</name></person></invoice>");
 
        template.sendBodyAndHeader("direct:in", "<response>OK</response>",
                                   "invoiceDetails", "<invoice orderType='standard'><person><name>Alan</name></person></invoice>");

        mock.assertIsSatisfied();
    }

    @Test
    public void testChoiceWithHeaderNameUnknown() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unknown");
        mock.expectedBodiesReceived("<response>OK</response>");
        mock.expectedHeaderReceived("invoiceDetails", "<invoice />");
 
        template.sendBodyAndHeader("direct:in", "<response>OK</response>",
                                   "invoiceDetails", "<invoice />");

        mock.assertIsSatisfied();
    }
}
