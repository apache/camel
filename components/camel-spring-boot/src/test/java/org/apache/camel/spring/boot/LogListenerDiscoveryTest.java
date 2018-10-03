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
package org.apache.camel.spring.boot;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.LogListener;
import org.apache.camel.support.CamelLogger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext
@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
        CamelAutoConfiguration.class,
        LogListenerDiscoveryTest.MyLogListener.class
    }
)
public class LogListenerDiscoveryTest {
    @Autowired
    private CamelContext camelContext;
    @Autowired
    private MyLogListener logListener;

    @Test
    public void testUuidDiscovery() {
        assertThat(camelContext.getLogListeners()).contains(logListener);
    }

    @Component
    public static class MyLogListener implements LogListener {
        @Override
        public String onLog(Exchange exchange, CamelLogger logger, String s) {
            return null;
        }
    }
}
