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
package org.apache.camel.dataformat.hessian;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Test for {@link HessianDataFormat}.
 */
public class HessianDataFormatWhitelistingTest extends CamelTestSupport {

    @Test
    public void testMarshalAndUnmarshalObject() throws Exception {
        final TestObject object = new TestObject();
        object.setBool(true);
        object.setIntNumber(42);
        object.setFloatNumber(3.14159f);
        object.setCharacter('Z');
        object.setText("random text");

        testMarshalAndUnmarshalFailed(object);
        
        final AnotherObject diffObject = new AnotherObject();
        object.setBool(true);
        object.setIntNumber(45);

        testMarshalAndUnmarshalSuccess(diffObject);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                final HessianDataFormat format = new HessianDataFormat();
                format.setWhitelistEnabled(true);
                format.setDeniedUnmarshallObjects("org.apache.camel.dataformat.hessian.TestObject");
                format.setAllowedUnmarshallObjects("org.apache.camel.dataformat.hessian.AnotherObject");

                from("direct:in").marshal(format);
                from("direct:back").unmarshal(format).to("mock:reverse");
            }
        };
    }
    
    private void testMarshalAndUnmarshalFailed(final Object object) throws Exception {
        final MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(1);

        if (object == null) {
            mock.message(0).body().isNull();
        } else {
            mock.message(0).body().isNotNull();
            mock.message(0).body().isNotEqualTo(object);
        }

        final Object marshalled = template.requestBody("direct:in", object);
        template.sendBody("direct:back", marshalled);

        mock.assertIsSatisfied();
    }
    
    private void testMarshalAndUnmarshalSuccess(final Object object) throws Exception {
        final MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(2);

        if (object == null) {
            mock.message(1).body().isNull();
        } else {
            mock.message(1).body().isNotNull();
            mock.message(1).body().isEqualTo(object);
        }

        final Object marshalled = template.requestBody("direct:in", object);
        template.sendBody("direct:back", marshalled);

        mock.assertIsSatisfied();
    }
}