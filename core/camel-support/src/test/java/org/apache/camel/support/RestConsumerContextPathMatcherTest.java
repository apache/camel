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
package org.apache.camel.support;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RestConsumerContextPathMatcherTest {

    private static final class MockConsumerPath implements RestConsumerContextPathMatcher.ConsumerPath<MockConsumerPath> {
        private final String method;
        private final String consumerPath;

        private MockConsumerPath(String method, String consumerPath) {
            this.method = method;
            this.consumerPath = consumerPath;
        }

        @Override
        public String getRestrictMethod() {
            return method;
        }

        @Override
        public String getConsumerPath() {
            return consumerPath;
        }

        @Override
        public MockConsumerPath getConsumer() {
            return null;
        }

        @Override
        public boolean isMatchOnUriPrefix() {
            return false;
        }
    }

    @Test
    public void testRestConsumerContextPathMatcherWithAmbiguousPaths() {
        List<RestConsumerContextPathMatcher.ConsumerPath<MockConsumerPath>> consumerPaths = new ArrayList<>();
        consumerPaths.add(new MockConsumerPath("GET", "/camel/{a}/b/{c}"));
        consumerPaths.add(new MockConsumerPath("GET", "/camel/a/{b}/{c}"));

        IllegalStateException illegalStateException = assertThrows(IllegalStateException.class, () -> {
            RestConsumerContextPathMatcher.matchBestPath("GET", "/camel/a/b/3", consumerPaths);
        });
        assertEquals("Ambiguous paths /camel/{a}/b/{c},/camel/a/{b}/{c} for request path /camel/a/b/3",
                illegalStateException.getMessage());
    }

    @Test
    public void testRestConsumerContextPathMatcherSuccess() {
        List<RestConsumerContextPathMatcher.ConsumerPath<MockConsumerPath>> consumerPaths = new ArrayList<>();
        consumerPaths.add(new MockConsumerPath("GET", "/camel/a/b/{c}"));
        consumerPaths.add(new MockConsumerPath("GET", "/camel/aa/{b}/{c}"));

        RestConsumerContextPathMatcher.ConsumerPath<?> path = RestConsumerContextPathMatcher.matchBestPath("GET",
                "/camel/a/b/3", consumerPaths);
        assertEquals(path.getConsumerPath(), "/camel/a/b/{c}");
    }
}
