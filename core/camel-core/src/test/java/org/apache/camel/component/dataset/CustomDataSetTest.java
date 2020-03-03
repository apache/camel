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
package org.apache.camel.component.dataset;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.language.xpath.XPathBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.PredicateAssertHelper;
import org.junit.Test;

public class CustomDataSetTest extends ContextTestSupport {

    protected DataSet dataSet = new DataSetSupport() {
        Expression expression = new XPathBuilder("/message/@index").resultType(Long.class);

        @Override
        public void assertMessageExpected(DataSetEndpoint dataSetEndpoint, Exchange expected, Exchange actual, long index) throws Exception {
            // lets compare the XPath result
            Predicate predicate = PredicateBuilder.isEqualTo(expression, ExpressionBuilder.constantExpression(index));
            log.debug("evaluating predicate: " + predicate);
            PredicateAssertHelper.assertMatches(predicate, "Actual: " + actual, actual);
        }

        protected Object createMessageBody(long messageIndex) {
            return "<message index='" + messageIndex + "'>someBody" + messageIndex + "</message>";
        }
    };

    @Override
    protected Registry createRegistry() throws Exception {
        Registry answer = super.createRegistry();
        answer.bind("foo", dataSet);
        return answer;
    }

    @Test
    public void testUsingCustomDataSet() throws Exception {
        // data set will itself set its assertions so we should just
        // assert that all mocks is ok
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("dataset:foo?initialDelay=0").to("direct:foo");

                from("direct:foo").to("dataset:foo?initialDelay=0");
            }
        };
    }
}
