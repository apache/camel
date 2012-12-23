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
package org.apache.camel.component.validator;

import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class ValidatorIncludeRelativeRouteTest extends ValidatorIncludeRouteTest {
    
    public void testValidMessage() throws Exception {
        validEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);
        
        String body = "<p:person user=\"james\" xmlns:p=\"org.person\" xmlns:h=\"org.health.check.person\" xmlns:c=\"org.health.check.common\">\n"
                + "  <p:firstName>James</p:firstName>\n"
                + "  <p:lastName>Strachan</p:lastName>\n"
                + "  <p:city>London</p:city>\n"
                + "  <h:health>\n"
                + "      <h:lastCheck>2011-12-23</h:lastCheck>\n"
                + "      <h:status>OK</h:status>\n"
                + "      <c:commonElement>" 
                + "          <c:element1/>"
                + "          <c:element2/>"
                + "      </c:commonElement>"
                + "  </h:health>\n"
                + "</p:person>";

        template.sendBody("direct:start", body);

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .doTry()
                        .to("validator:org/apache/camel/component/validator/xsds/person.xsd")
                        .to("mock:valid")
                    .doCatch(ValidationException.class)
                        .to("mock:invalid")
                    .doFinally()
                        .to("mock:finally")
                    .end();
            }
        };
    }

}
