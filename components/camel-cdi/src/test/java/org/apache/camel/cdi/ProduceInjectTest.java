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
package org.apache.camel.cdi;

import javax.inject.Inject;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.cdi.support.ProduceInjectedBean;
import org.junit.Test;

/**
 * Test endpoint injection
 */
public class ProduceInjectTest extends CdiTestSupport {

    @Inject
    private ProduceInjectedBean bean;

    @Test
    public void shouldInjectEndpoint() {
        assertNotNull(bean);
        ProducerTemplate producer = bean.getProducer();
        assertNotNull("Could not find injected producer!", producer);
        assertEquals("producer default URI", "mock://foo", producer.getDefaultEndpoint().getEndpointUri());

        ProducerTemplate producer2 = bean.getProducer2();
        assertNotNull("Could not find injected producer2!", producer2);
        assertEquals("producer2 default URI", "mock://bar", producer2.getDefaultEndpoint().getEndpointUri());
    }

}
