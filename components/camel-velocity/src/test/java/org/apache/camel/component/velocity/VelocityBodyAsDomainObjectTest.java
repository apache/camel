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
package org.apache.camel.component.velocity;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Unit test with the body as a Domain object.
 *
 * @version 
 */
public class VelocityBodyAsDomainObjectTest extends CamelTestSupport {

    @Test
    public void testWithObject() throws Exception {
        String msg = String.format("Hi Claus how are you? Its a nice day.%sGive my regards to the family Ibsen.", "\n");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(msg);

        MyPerson person = new MyPerson();
        person.setFamilyName("Ibsen");
        person.setGivenName("Claus");

        template.requestBody("direct:in", person);

        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in")
                    .to("velocity:org/apache/camel/component/velocity/BodyAsDomainObject.vm")
                    .to("mock:result");
            }
        };
    }

    public static class MyPerson {
        private String givenName;
        private String familyName;

        public String getGivenName() {
            return givenName;
        }

        public void setGivenName(String givenName) {
            this.givenName = givenName;
        }

        public String getFamilyName() {
            return familyName;
        }

        public void setFamilyName(String familyName) {
            this.familyName = familyName;
        }

        public String toString() {
            return "MyPerson{"
                + "givenName='"
                + givenName + '\''
                + ", familyName='"
                + familyName + '\''
                + '}';
        }
    }

}