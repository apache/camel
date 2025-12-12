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
package org.apache.camel.component.dapr;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DaprConfigurationOptionsProxyTest extends CamelTestSupport {

    @Test
    void testIfCorrectOptionsReturnedCorrectly() {
        final DaprConfiguration configuration = new DaprConfiguration();

        // first case: when exchange is set
        final Exchange exchange = new DefaultExchange(context);
        final DaprConfigurationOptionsProxy configurationOptionsProxy = new DaprConfigurationOptionsProxy(configuration);

        exchange.getIn().setHeader(DaprConstants.METHOD_TO_INVOKE, "exchangeMethod");
        configuration.setMethodToInvoke("configMethod");

        assertEquals("exchangeMethod", configurationOptionsProxy.getMethodToInvoke(exchange));

        // second class: exchange is empty
        exchange.getIn().setHeader(DaprConstants.METHOD_TO_INVOKE, null);

        assertEquals("configMethod", configurationOptionsProxy.getMethodToInvoke(exchange));

        // third class: if no option at all
        configuration.setMethodToInvoke(null);

        assertNull(configurationOptionsProxy.getMethodToInvoke(exchange));
    }
}
