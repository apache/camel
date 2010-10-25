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
package org.apache.camel.processor;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.validation.NoXmlBodyValidationException;

/**
 * Unit test of ValidatingProcessor.
 */
public class ValidatingDomProcessorTest extends ValidatingProcessorTest {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        validating.setUseDom(true);
        assertEquals(true, validating.isUseDom());
    }

    public void testNonWellFormedXml() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:invalid");
        mock.expectedMessageCount(1);

        String xml = "<?xml version=\"1.0\" encoding=\"iso-8859-1\"?>"
            + "user xmlns=\"http://foo.com/bar\">"
            + "  <id>1</id>"
            + "  <username>davsclaus</username>";

        try {
            template.sendBody("direct:start", xml);
            fail("Should have thrown a RuntimeCamelException");
        } catch (CamelExecutionException e) {
            // cannot be converted to DOM
            assertIsInstanceOf(NoXmlBodyValidationException.class, e.getCause());
        }

        assertMockEndpointsSatisfied();
    }

}