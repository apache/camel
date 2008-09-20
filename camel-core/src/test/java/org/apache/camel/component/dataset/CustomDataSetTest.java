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
package org.apache.camel.component.dataset;

import javax.naming.Context;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.XPathBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class CustomDataSetTest extends ContextTestSupport {
    protected DataSet dataSet = new DataSetSupport() {
        Expression<Exchange> expression = new XPathBuilder<Exchange>("/message/@index").resultType(Long.class);

        @Override
        public void assertMessageExpected(DataSetEndpoint dataSetEndpoint, Exchange expected, Exchange actual, long index) throws Exception {
            // lets compare the XPath result
            Predicate<Exchange> predicate = PredicateBuilder.isEqualTo(expression, ExpressionBuilder.constantExpression(index));
            log.debug("evaluating predicate: " + predicate);
            predicate.assertMatches("Actual: " + actual, actual);
        }

        protected Object createMessageBody(long messageIndex) {
            return "<message index='" + messageIndex + "'>someBody" + messageIndex + "</message>";
        }
    };

    public void testUsingCustomDataSet() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:results");
        endpoint.expectedMessageCount((int) dataSet.getSize());

        assertMockEndpointsSatisfied();
    }

    @Override
    protected Context createJndiContext() throws Exception {
        Context context = super.createJndiContext();
        context.bind("foo", dataSet);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("dataset:foo").multicast().
                        to("mock:results").
                        to("direct:foo");

                from("direct:foo").to("dataset:foo");
            }
        };
    }
}