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
package org.apache.camel.component.atomix.client.map;

import io.atomix.collections.DistributedMap;
import org.apache.camel.Message;
import org.apache.camel.builder.DefaultFluentProducerTemplate;
import org.apache.camel.component.atomix.client.AtomixClientConstants;
import org.apache.camel.component.atomix.client.AtomixClientSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext
public class SpringAtomixMapProducerTest extends AtomixClientSpringTestSupport {

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/atomix/client/map/SpringAtomixMapProducerTest.xml");
    }

    @Test
    public void testPut() throws Exception {
        final String key = context().getUuidGenerator().generateUuid();
        final String val = context().getUuidGenerator().generateUuid();
        final DistributedMap<Object, Object> map = getClient().getMap("test-map").join();

        Message result = DefaultFluentProducerTemplate.on(context)
            .withHeader(AtomixClientConstants.RESOURCE_ACTION, AtomixMap.Action.PUT)
            .withHeader(AtomixClientConstants.RESOURCE_KEY, key)
            .withBody(val)
            .to("direct:start")
            .request(Message.class);

        assertFalse(result.getHeader(AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT, Boolean.class));
        assertEquals(val, result.getBody());
        assertEquals(val, map.get(key).join());
    }
}
