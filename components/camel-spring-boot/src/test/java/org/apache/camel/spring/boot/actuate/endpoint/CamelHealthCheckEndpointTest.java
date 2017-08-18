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

package org.apache.camel.spring.boot.actuate.endpoint;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@DirtiesContext
@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
        CamelAutoConfiguration.class,
        CamelHealthCheckEndpointAutoConfiguration.class,
        CamelHealthCheckEndpointTest.TestConfiguration.class
    },
    properties = {

    }
)
public class CamelHealthCheckEndpointTest {
    @Autowired
    private CamelHealthCheckEndpoint endpoint;

    @Test
    public void testHealthCheckEndpoint() throws Exception {
        Collection<CamelHealthCheckEndpoint.HealthCheckResult> results = endpoint.invoke();

        Assert.assertNotNull(results);
        Assert.assertEquals(2, results.size());
        Assert.assertEquals(1, results.stream().filter(r -> r.getCheck().getId().equals("check-1") && r.getStatus().equals(HealthCheck.State.UP.name())).count());
        Assert.assertEquals(1, results.stream().filter(r -> r.getCheck().getId().equals("check-2") && r.getStatus().equals(HealthCheck.State.DOWN.name())).count());
    }

    @Test
    public void testInvokeHealthCheckEndpoint() throws Exception {
        Optional<CamelHealthCheckEndpoint.HealthCheckResult> result1 = endpoint.invoke("check-1", null);
        Optional<CamelHealthCheckEndpoint.HealthCheckResult> result2 = endpoint.invoke("check-2", null);
        Optional<CamelHealthCheckEndpoint.HealthCheckResult> result3 = endpoint.invoke("check-3", null);

        Assert.assertTrue(result1.isPresent());
        Assert.assertEquals("check-1", result1.get().getCheck().getId());
        Assert.assertEquals(HealthCheck.State.UP.name(), result1.get().getStatus());

        Assert.assertTrue(result2.isPresent());
        Assert.assertEquals("check-2", result2.get().getCheck().getId());
        Assert.assertEquals(HealthCheck.State.DOWN.name(), result2.get().getStatus());

        Assert.assertFalse(result3.isPresent());
    }

    public static class TestConfiguration {
        @Bean
        public HealthCheck check1() {
            MyCheck check = new MyCheck("test", "check-1");
            check.getConfiguration().setEnabled(true);
            check.setState(HealthCheck.State.UP);

            return check;
        }

        @Bean
        public HealthCheck check2() {
            MyCheck check = new MyCheck("test", "check-2");
            check.getConfiguration().setEnabled(true);
            check.setState(HealthCheck.State.DOWN);

            return check;
        }
    }

    public static class MyCheck extends AbstractHealthCheck {
        private State state;

        public MyCheck(String group, String id) {
            super(group, id);
        }


        public State getState() {
            return state;
        }

        public void setState(State state) {
            this.state = state;
        }

        @Override
        protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
            builder.state(state);
        }
    }
}
