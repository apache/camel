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
package org.apache.camel.component.aws.xray;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.component.aws.xray.TestDataBuilder.TestTrace;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;

public class SpringAwsXRaySimpleRouteTest extends CamelSpringTestSupport {

    @Rule
    public FakeAWSDaemon socketListener = new FakeAWSDaemon();

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/aws/xray/AwsXRaySimpleRouteTest.xml");
    }

    @Test
    public void testRoute() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(5).create();

        for (int i = 0; i < 5; i++) {
            template.sendBody("seda:dude", "Hello World");
        }

        assertThat("Not all exchanges were fully processed",
                notify.matches(30, TimeUnit.SECONDS), is(equalTo(true)));

        List<TestTrace> testData = Arrays.asList(
        TestDataBuilder.createTrace()
            .withSegment(TestDataBuilder.createSegment("dude"))
            .withSegment(TestDataBuilder.createSegment("car")),
        TestDataBuilder.createTrace()
            .withSegment(TestDataBuilder.createSegment("dude"))
            .withSegment(TestDataBuilder.createSegment("car")),
        TestDataBuilder.createTrace()
            .withSegment(TestDataBuilder.createSegment("dude"))
            .withSegment(TestDataBuilder.createSegment("car")),
        TestDataBuilder.createTrace()
            .withSegment(TestDataBuilder.createSegment("dude"))
            .withSegment(TestDataBuilder.createSegment("car")),
        TestDataBuilder.createTrace()
            .withSegment(TestDataBuilder.createSegment("dude"))
            .withSegment(TestDataBuilder.createSegment("car"))
        );

        Thread.sleep(2000);

        TestUtils.checkData(socketListener.getReceivedData(), testData);
    }
}
