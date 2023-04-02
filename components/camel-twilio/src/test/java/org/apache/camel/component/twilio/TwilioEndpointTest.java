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
package org.apache.camel.component.twilio;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TwilioEndpointTest extends AbstractTwilioTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        PluginHelper.getBeanIntrospection(context).setLoggingLevel(LoggingLevel.INFO);
        return context;
    }

    @Test
    public void testTwilioEndpoint() {
        // should not use reflection when creating and configuring endpoint
        final BeanIntrospection beanIntrospection = PluginHelper.getBeanIntrospection(context);
        long before = beanIntrospection.getInvokedCounter();

        TwilioEndpoint te = context.getEndpoint("twilio:account/fetcher?pathSid=123", TwilioEndpoint.class);
        AccountEndpointConfiguration aec = (AccountEndpointConfiguration) te.getConfiguration();
        Assertions.assertEquals("123", aec.getPathSid());

        long after = beanIntrospection.getInvokedCounter();
        Assertions.assertEquals(before, after);
    }
}
