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
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    public void testRestConsumerContextPathMatcherWithWildcard() {
        List<RestConsumerContextPathMatcher.ConsumerPath<MockConsumerPath>> consumerPaths = new ArrayList<>();
        consumerPaths.add(new MockConsumerPath("GET", "/camel/myapp/info"));
        consumerPaths.add(new MockConsumerPath("GET", "/camel/myapp/{id}"));
        consumerPaths.add(new MockConsumerPath("GET", "/camel/myapp/order/*"));

        RestConsumerContextPathMatcher.register("/camel/myapp/order/*");

        RestConsumerContextPathMatcher.ConsumerPath<?> path1 = RestConsumerContextPathMatcher.matchBestPath("GET",
                "/camel/myapp/info", consumerPaths);

        RestConsumerContextPathMatcher.ConsumerPath<?> path2 = RestConsumerContextPathMatcher.matchBestPath("GET",
                "/camel/myapp/1", consumerPaths);

        RestConsumerContextPathMatcher.ConsumerPath<?> path3 = RestConsumerContextPathMatcher.matchBestPath("GET",
                "/camel/myapp/order/foo", consumerPaths);

        assertEquals(path1.getConsumerPath(), "/camel/myapp/info");
        assertEquals(path2.getConsumerPath(), "/camel/myapp/{id}");
        assertEquals(path3.getConsumerPath(), "/camel/myapp/order/*");
    }

    @Test
    public void testRestConsumerContextPathMatcherOrder() {
        List<RestConsumerContextPathMatcher.ConsumerPath<MockConsumerPath>> consumerPaths = new ArrayList<>();
        consumerPaths.add(new MockConsumerPath("GET", "/camel/*"));
        consumerPaths.add(new MockConsumerPath("GET", "/camel/foo"));
        consumerPaths.add(new MockConsumerPath("GET", "/camel/foo/{id}"));

        RestConsumerContextPathMatcher.register("/camel/*");

        RestConsumerContextPathMatcher.ConsumerPath<?> path1 = RestConsumerContextPathMatcher.matchBestPath("GET",
                "/camel/foo", consumerPaths);

        RestConsumerContextPathMatcher.ConsumerPath<?> path2 = RestConsumerContextPathMatcher.matchBestPath("GET",
                "/camel/foo/bar", consumerPaths);

        RestConsumerContextPathMatcher.ConsumerPath<?> path3 = RestConsumerContextPathMatcher.matchBestPath("GET",
                "/camel/foo/bar/1", consumerPaths);

        assertEquals(path1.getConsumerPath(), "/camel/foo");
        assertEquals(path2.getConsumerPath(), "/camel/foo/{id}");
        assertEquals(path3.getConsumerPath(), "/camel/*");
    }

    @Test
    public void testRestConsumerContextPathMatcherPetStore() {
        final List<RestConsumerContextPathMatcher.ConsumerPath<MockConsumerPath>> consumerPaths = createConsumerPaths();

        RestConsumerContextPathMatcher.register("/api/v3/*");

        RestConsumerContextPathMatcher.ConsumerPath<?> path1 = RestConsumerContextPathMatcher.matchBestPath("GET",
                "/pet", consumerPaths);
        assertNull(path1);
        RestConsumerContextPathMatcher.ConsumerPath<?> path2 = RestConsumerContextPathMatcher.matchBestPath("POST",
                "/pet", consumerPaths);
        assertEquals(path2.getConsumerPath(), "/pet");

        RestConsumerContextPathMatcher.ConsumerPath<?> path3 = RestConsumerContextPathMatcher.matchBestPath("GET",
                "/pet/findByStatus", consumerPaths);
        assertEquals(path3.getConsumerPath(), "/pet/findByStatus");
        RestConsumerContextPathMatcher.ConsumerPath<?> path4 = RestConsumerContextPathMatcher.matchBestPath("DELETE",
                "/pet/findByStatus", consumerPaths);
        assertNull(path4);

        RestConsumerContextPathMatcher.ConsumerPath<?> path5 = RestConsumerContextPathMatcher.matchBestPath("GET",
                "/pet/findByTags", consumerPaths);
        assertEquals(path5.getConsumerPath(), "/pet/findByTags");
        RestConsumerContextPathMatcher.ConsumerPath<?> path6 = RestConsumerContextPathMatcher.matchBestPath("POST",
                "/pet/findByStatus", consumerPaths);
        assertNull(path6);

        RestConsumerContextPathMatcher.ConsumerPath<?> path7 = RestConsumerContextPathMatcher.matchBestPath("GET",
                "/pet/123", consumerPaths);
        assertEquals(path7.getConsumerPath(), "/pet/{petId}");
        RestConsumerContextPathMatcher.ConsumerPath<?> path8 = RestConsumerContextPathMatcher.matchBestPath("POST",
                "/pet/222", consumerPaths);
        assertEquals(path8.getConsumerPath(), "/pet/{petId}");
        RestConsumerContextPathMatcher.ConsumerPath<?> path9 = RestConsumerContextPathMatcher.matchBestPath("DELETE",
                "/pet/333", consumerPaths);
        assertEquals(path9.getConsumerPath(), "/pet/{petId}");
        RestConsumerContextPathMatcher.ConsumerPath<?> path10 = RestConsumerContextPathMatcher.matchBestPath("PUT",
                "/pet/444", consumerPaths);
        assertNull(path10);

        RestConsumerContextPathMatcher.ConsumerPath<?> path11 = RestConsumerContextPathMatcher.matchBestPath("POST",
                "/pet/123/uploadImage", consumerPaths);
        assertEquals(path11.getConsumerPath(), "/pet/{petId}/uploadImage");
        RestConsumerContextPathMatcher.ConsumerPath<?> path12 = RestConsumerContextPathMatcher.matchBestPath("DELETE",
                "/pet/222/uploadImage", consumerPaths);
        assertNull(path12);
    }

    private static List<RestConsumerContextPathMatcher.ConsumerPath<MockConsumerPath>> createConsumerPaths() {
        List<RestConsumerContextPathMatcher.ConsumerPath<MockConsumerPath>> consumerPaths = new ArrayList<>();
        consumerPaths.add(new MockConsumerPath("POST", "/pet"));
        consumerPaths.add(new MockConsumerPath("PUT", "/pet"));
        consumerPaths.add(new MockConsumerPath("GET", "/pet/findByStatus"));
        consumerPaths.add(new MockConsumerPath("GET", "/pet/findByTags"));
        consumerPaths.add(new MockConsumerPath("DELETE", "/pet/{petId}"));
        consumerPaths.add(new MockConsumerPath("GET", "/pet/{petId}"));
        consumerPaths.add(new MockConsumerPath("POST", "/pet/{petId}"));
        consumerPaths.add(new MockConsumerPath("POST", "/pet/{petId}/uploadImage"));
        return consumerPaths;
    }

}
