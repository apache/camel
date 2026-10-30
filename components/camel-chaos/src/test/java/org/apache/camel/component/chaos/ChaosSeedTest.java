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
package org.apache.camel.component.chaos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The seed fixes the random draws of the rules: the same seed and the same order of the exchanges give the same faults.
 */
class ChaosSeedTest {

    private static List<Integer> failedSends(String seed) throws Exception {
        CamelContext context = new DefaultCamelContext();
        try {
            Map.of("camel.chaos.enabled", "true", "camel.chaos.seed", seed,
                    "camel.chaos.rules", "endpoint=mock:a error rate=0.3; endpoint=mock:b drop rate=0.5")
                    .forEach(context.getPropertiesComponent()::addInitialProperty);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").to("mock:a").to("mock:b").to("mock:c");
                }
            });
            context.start();
            ProducerTemplate template = context.createProducerTemplate();
            List<Integer> failed = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                if (template.send("direct:start", e -> e.getMessage().setBody("x")).getException() != null) {
                    failed.add(i);
                }
            }
            // the drops of the second rule are part of the sequence too
            failed.add(-1);
            failed.add(context.getEndpoint("mock:c", MockEndpoint.class)
                    .getReceivedCounter());
            return failed;
        } finally {
            context.stop();
        }
    }

    @Test
    void sameSeedSameFaults() throws Exception {
        List<Integer> first = failedSends("42");
        List<Integer> second = failedSends("42");
        assertThat(first).isEqualTo(second);
        assertThat(first.size()).isGreaterThan(30);
    }

    @Test
    void otherSeedOtherFaults() throws Exception {
        assertThat(failedSends("42")).isNotEqualTo(failedSends("43"));
    }
}
