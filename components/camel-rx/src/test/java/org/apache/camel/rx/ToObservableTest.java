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
package org.apache.camel.rx;

import org.apache.camel.Message;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;

public class ToObservableTest extends RxTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ToObservableTest.class);

    @Test
    public void testConsume() throws Exception {
        final MockEndpoint mockEndpoint = camelContext.getEndpoint("mock:results", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(4);

        Observable<Message> observable = reactiveCamel.toObservable("timer://foo?fixedRate=true&period=100");
        observable.take(4).subscribe(message -> {
            String body = "Processing message headers " + message.getHeaders();
            LOG.info(body);
            producerTemplate.sendBody(mockEndpoint, body);
        });

        mockEndpoint.assertIsSatisfied();
    }
}
