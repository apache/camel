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
package org.apache.camel.component.twitter.deprecated;

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.twitter.CamelTwitterTestSupport;
import org.junit.Ignore;
import org.junit.Test;

@Deprecated
@Ignore
public class UserStreamingTest extends CamelTwitterTestSupport  {

    @Test
    public void testUserStreaming() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:user-streaming");
        mock.setResultWaitTime(TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES));
        mock.expectedMessageCount(1);
        mock.assertIsSatisfied();
    }


    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("twitter://streaming/user?type=event&" + getUriTokens())
                    .to("log:org.apache.camel.component.twitter?level=INFO&showHeaders=true")
                    .to("mock:user-streaming");
            }
        };
    }
}
