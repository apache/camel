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
package org.apache.camel.example.cdi.test;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.cdi.Uri;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.cdi.Beans;
import org.apache.camel.test.cdi.CamelCdiRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;

@RunWith(CamelCdiRunner.class)
@Beans(
    alternatives = AlternativeBean.class,
    classes = TestRoute.class
)
public class AlternativeTest {

    @Inject
    @Uri("mock:out")
    MockEndpoint mock;

    @Inject
    @Uri("direct:in")
    ProducerTemplate producer;

    @Test
    public void testAlternativeBean() throws InterruptedException {
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("test with alternative bean!");

        producer.sendBody("test");

        assertIsSatisfied(1L, TimeUnit.SECONDS, mock);
    }
}
