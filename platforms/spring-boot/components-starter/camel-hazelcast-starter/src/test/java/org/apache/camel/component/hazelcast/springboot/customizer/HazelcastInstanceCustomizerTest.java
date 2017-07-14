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
package org.apache.camel.component.hazelcast.springboot.customizer;


import java.util.List;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.component.hazelcast.HazelcastComponent;
import org.apache.camel.component.hazelcast.list.HazelcastListComponent;
import org.apache.camel.component.hazelcast.set.HazelcastSetComponent;
import org.apache.camel.component.hazelcast.topic.HazelcastTopicComponent;
import org.apache.camel.spi.ComponentCustomizer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@DirtiesContext
@SpringBootApplication
@SpringBootTest(
    classes = {
        HazelcastInstanceCustomizerTest.TestConfiguration.class
    },
    properties = {
        "debug=false",
        "camel.component.customizer.enabled=false",
        "camel.component.hazelcast.customizer.enabled=true",
        "camel.component.hazelcast-set.customizer.enabled=true",
        "camel.component.hazelcast-topic.customizer.enabled=true"
    })
public class HazelcastInstanceCustomizerTest {
    @Autowired
    HazelcastInstance instance;
    @Autowired
    HazelcastTopicComponent topicComponent;
    @Autowired
    List<ComponentCustomizer<HazelcastTopicComponent>> topicCustomizers;
    @Autowired
    HazelcastSetComponent setComponent;
    @Autowired
    List<ComponentCustomizer<HazelcastSetComponent>> setCustomizers;
    @Autowired
    HazelcastListComponent listComponent;

    @Test
    public void testInstanceCustomizer() throws Exception {
        Assert.assertNotNull(instance);

        Assert.assertNotNull(topicComponent);
        Assert.assertEquals(1, topicCustomizers.size());
        Assert.assertEquals(instance, topicComponent.getHazelcastInstance());

        Assert.assertNotNull(setComponent);
        Assert.assertEquals(1, setCustomizers.size());
        Assert.assertEquals(instance, setComponent.getHazelcastInstance());

        Assert.assertNotNull(listComponent);
        Assert.assertNull(listComponent.getHazelcastInstance());
    }

    @Configuration
    public static class TestConfiguration {
        @Bean(destroyMethod = "shutdown")
        public HazelcastInstance hazelcastInstance() {
            return Hazelcast.newHazelcastInstance();
        }
    }
}
