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
package org.apache.camel.component.aws.swf;

import com.amazonaws.services.simpleworkflow.model.ActivityTask;
import com.amazonaws.services.simpleworkflow.model.PollForActivityTaskRequest;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CamelSWFActivityConsumerTest extends CamelSWFTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            public void configure() throws Exception {
                from("aws-swf://activity?" + options)
                        .to("mock:result");
            }
        };
    }

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Test
    public void receivesDecisionTask() throws Exception {
        result.expectedMinimumMessageCount(1);
        result.expectedMessagesMatches(new Predicate() {
            public boolean matches(Exchange exchange) {
                return exchange.getIn().getHeader(SWFConstants.ACTION).equals(SWFConstants.EXECUTE_ACTION)
                        && exchange.getIn().getBody(Object[].class)[0].equals("test");
            }
        });

        ActivityTask activityTask = new ActivityTask();
        activityTask.setTaskToken("token");
        activityTask.setInput("[\"[Ljava.lang.Object;\",[\"test\"]]");
        when(amazonSWClient.pollForActivityTask(any(PollForActivityTaskRequest.class))).thenReturn(activityTask);

        context.start();

        assertMockEndpointsSatisfied();
        verify(amazonSWClient, atLeastOnce()).pollForActivityTask(any(PollForActivityTaskRequest.class));
    }
}
