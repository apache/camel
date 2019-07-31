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
package org.apache.camel.component.hipchat;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class HipchatEndpointTest {
    @Test
    public void testCreateConsumer() throws Exception {
        HipchatComponent component = new HipchatComponent(Mockito.mock(CamelContext.class));
        HipchatEndpoint endpoint = new HipchatEndpoint("hipchat:http://api.hipchat.com?authKey=token", component);
        HipchatConsumer consumer = (HipchatConsumer)endpoint.createConsumer(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {

            }
        });

        Assert.assertEquals(5000, consumer.getDelay());
    }
}
