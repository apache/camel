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
package org.apache.camel.spring.config;

import java.util.List;

import org.apache.camel.Channel;
import org.apache.camel.Route;
import org.apache.camel.impl.engine.DefaultRoute;
import org.apache.camel.processor.errorhandler.DeadLetterChannel;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.spring.SpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class ErrorHandlerTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/config/errorHandler.xml");
    }

    @Test
    public void testEndpointConfiguration() throws Exception {
        SpringCamelContext context = applicationContext.getBeansOfType(SpringCamelContext.class).values().iterator().next();
        List<Route> list = context.getRoutes();
        assertEquals("Number routes created" + list, 2, list.size());
        for (Route route : list) {
            DefaultRoute consumerRoute = assertIsInstanceOf(DefaultRoute.class, route);
            Channel channel = unwrapChannel(consumerRoute.getProcessor());

            DeadLetterChannel deadLetterChannel = assertIsInstanceOf(DeadLetterChannel.class, channel.getErrorHandler());
            RedeliveryPolicy redeliveryPolicy = deadLetterChannel.getRedeliveryPolicy();

            assertEquals("getMaximumRedeliveries()", 1, redeliveryPolicy.getMaximumRedeliveries());
            assertEquals("isUseExponentialBackOff()", true, redeliveryPolicy.isUseExponentialBackOff());
        }
    }

}
