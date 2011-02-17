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
import ca.uhn.hl7v2.model.DataTypeException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class HL7ValidateTest extends CamelTestSupport {

    @Test
    public void testUnmarshalFailed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
        mock.expectedMessageCount(0);

        String body = createHL7AsString();
        try {
            template.sendBody("direct:unmarshalFailed", body);
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(HL7Exception.class, e.getCause());
            assertIsInstanceOf(DataTypeException.class, e.getCause());
            assertTrue("Should be a validation error message", e.getCause().getMessage().startsWith("Failed validation rule"));
        }

        assertMockEndpointsSatisfied();
    }

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
        String line1 = "MSH|^~\\&|REQUESTING|ICE|INHOUSE|RTH00|20080808093202||ORM^O01|0808080932027444985|P|2.4|||AL|NE|||";
        String line2 = "PID|1||ICE999999^^^ICE^ICE||Testpatient^Testy^^^Mr||19740401|M|||123 Barrel Drive^^^^SW18 4RT|||||2||||||||||||||";
        String line3 = "NTE|1||Free text for entering clinical details|";
        String line4 = "PV1|1||^^^^^^^^Admin Location|||||||||||||||NHS|";
        String line5 = "ORC|NW|213||175|REQ||||20080808093202|ahsl^^Administrator||G999999^TestDoctor^GPtests^^^^^^NAT|^^^^^^^^Admin Location | 819600|200808080932||RTH00||ahsl^^Administrator||";
        String line6 = "OBR|1|213||CCOR^Serum Cortisol ^ JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||";
        String line7 = "OBR|2|213||GCU^Serum Copper ^ JRH06 |||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||";
        String line8 = "OBR|3|213||THYG^Serum Thyroglobulin ^JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||";

        StringBuilder body = new StringBuilder();
        body.append(line1);
        body.append("\n");
        body.append(line2);
        body.append("\n");
        body.append(line3);
        body.append("\n");
        body.append(line4);
        body.append("\n");
        body.append(line5);
        body.append("\n");
        body.append(line6);
        body.append("\n");
        body.append(line7);
        body.append("\n");
        body.append(line8);
        return body.toString();
    }

}
