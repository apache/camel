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
package org.apache.camel.component.hl7;

import ca.uhn.hl7v2.HL7Exception;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version $Revision$
 */
public class HL7ValidateTest extends CamelTestSupport {

// TODO: Need HL7 that can fail HL7 validator
//    @Test
//    public void testUnmarshalFailed() throws Exception {
//        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
//        mock.expectedMessageCount(0);
//
//        String body = createHL7AsString();
//        try {
//            template.sendBody("direct:unmarshalFailed", body);
//            fail("Should have thrown exception");
//        } catch (CamelExecutionException e) {
//            assertIsInstanceOf(HL7Exception.class, e.getCause());
//        }
//
//        assertMockEndpointsSatisfied();
//    }

    @Test
    public void testUnmarshalOk() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
        mock.expectedMessageCount(1);

        String body = createHL7AsString();
        template.sendBody("direct:unmarshalOk", body);

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:unmarshalFailed").unmarshal().hl7().to("mock:unmarshal");

                from("direct:unmarshalOk").unmarshal().hl7(false).to("mock:unmarshal");
            }
        };
    }

    private static String createHL7AsString() {
        String line1 = "MSH|^~\\&|MYSENDER|MYSENDERAPP|MYCLIENT|MYCLIENTAPP|200612211200||QRY^A19|1234|P|2.4";
        String line2 = "QRD|200612211200|R|I|GetPatient|||1^RD|0101701234|DEM||";

        StringBuffer body = new StringBuffer();
        body.append(line1);
        body.append("\n");
        body.append(line2);
        return body.toString();
    }

}
