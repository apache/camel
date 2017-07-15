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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import static org.apache.camel.processor.ExchangeAwareDelayCalcBean.BEAN_DELAYER_HEADER;

/**
 * @version 
 */
public class DelayerTest extends ContextTestSupport {

    private MyDelayCalcBean bean = new MyDelayCalcBean();

    private ExchangeAwareDelayCalcBean exchangeAwareBean = new ExchangeAwareDelayCalcBean();

    public void testSendingMessageGetsDelayed() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);

        // do not wait for the first message
        resultEndpoint.expectedMessageCount(0);
        resultEndpoint.setResultWaitTime(10);
        template.sendBodyAndHeader("seda:a", "<hello>world!</hello>", "MyDelay", 100);
        // we should not receive it as we wait at most 0.01 sec and it take 0.1 sec to send
        resultEndpoint.assertIsSatisfied();

        // now if we wait a bit longer we should receive the message!
        resultEndpoint.reset();
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.assertIsSatisfied();
    }

    public void testDelayConstant() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        // should at least take 0.1 sec to complete
        resultEndpoint.setResultMinimumWaitTime(90);
        template.sendBody("seda:b", "<hello>world!</hello>");
        resultEndpoint.assertIsSatisfied();
    }

    public void testDelayBean() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        // should at least take 0.1 sec to complete
        resultEndpoint.setResultMinimumWaitTime(90);
        template.sendBody("seda:c", "<hello>world!</hello>");
        resultEndpoint.assertIsSatisfied();
    }

    public void testExchangeAwareDelayBean() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);
        // should at least take 0.1 sec to complete
        resultEndpoint.setResultMinimumWaitTime(90);
        template.sendBodyAndHeader("seda:d", "<hello>world!</hello>", BEAN_DELAYER_HEADER, 100);
        resultEndpoint.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: ex
                from("seda:a").delay().header("MyDelay").to("mock:result");
                // END SNIPPET: ex

                // START SNIPPET: ex2
                from("seda:b").delay(100).to("mock:result");
                // END SNIPPET: ex2

                // START SNIPPET: ex3
                from("seda:c").delay().method(bean, "delayMe").to("mock:result");
                // END SNIPPET: ex3

                from("seda:d").delay().method(exchangeAwareBean, "delayMe").to("mock:result");
            }
        };
    }
}
