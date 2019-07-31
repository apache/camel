/*
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
package org.apache.camel.dataformat.bindy.fixed;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.FixedLengthRecord;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.hamcrest.core.Is;
import org.junit.Test;

public class BindyPaddingAndTrimmingTest extends CamelTestSupport {

    private static final String URI_DIRECT_UNMARSHAL = "direct:unmarshall";
    private static final String URI_MOCK_UNMARSHAL_RESULT = "mock:unmarshal_result";

    @EndpointInject(URI_MOCK_UNMARSHAL_RESULT)
    private MockEndpoint unmarhsalResult;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(URI_DIRECT_UNMARSHAL)
                        .unmarshal().bindy(BindyType.Fixed, MyBindyModel.class)
                        .to(URI_MOCK_UNMARSHAL_RESULT);
            }
        };
    }

    @Test
    public void testUnmarshal() throws Exception {
        unmarhsalResult.expectedMessageCount(1);
        template.sendBody(URI_DIRECT_UNMARSHAL, "foo  \r\n");

        unmarhsalResult.assertIsSatisfied();
        MyBindyModel myBindyModel = unmarhsalResult.getReceivedExchanges().get(0).getIn().getBody(MyBindyModel.class);
        assertEquals("foo  ", myBindyModel.foo);
        assertThat(myBindyModel.bar, Is.is(""));
    }

    @Test
    public void testUnmarshalTooLong() throws Exception {
        unmarhsalResult.expectedMessageCount(1);
        template.sendBody(URI_DIRECT_UNMARSHAL, "foo  bar    \r\n");

        unmarhsalResult.assertIsSatisfied();
        MyBindyModel myBindyModel = unmarhsalResult.getReceivedExchanges().get(0).getIn().getBody(MyBindyModel.class);
        assertEquals("foo  ", myBindyModel.foo);

    }

    @FixedLengthRecord(length = 10, ignoreMissingChars = true, ignoreTrailingChars = true)
    public static class MyBindyModel {
        @DataField(pos = 0, length = 5)
        String foo;

        @DataField(pos = 5, length = 5)
        String bar;

        public String getFoo() {
            return foo;
        }

        public void setFoo(String foo) {
            this.foo = foo;
        }

        public String getBar() {
            return bar;
        }

        public void setBar(String bar) {
            this.bar = bar;
        }
    }
}