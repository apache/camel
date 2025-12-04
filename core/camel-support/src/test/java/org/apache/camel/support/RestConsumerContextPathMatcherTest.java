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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class RestConsumerContextPathMatcherTest {

    private static final class MockConsumerPath
            implements RestConsumerContextPathMatcher.ConsumerPath<MockConsumerPath> {
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
        assertEquals(
                "Ambiguous paths /camel/{a}/b/{c},/camel/a/{b}/{c} for request path /camel/a/b/3",
                illegalStateException.getMessage());
    }

    @Test
    public void testRestConsumerContextPathMatcherSuccess() {
        List<RestConsumerContextPathMatcher.ConsumerPath<MockConsumerPath>> consumerPaths = new ArrayList<>();
        consumerPaths.add(new MockConsumerPath("GET", "/camel/a/b/{c}"));
        consumerPaths.add(new MockConsumerPath("GET", "/camel/aa/{b}/{c}"));

        RestConsumerContextPathMatcher.ConsumerPath<?> path =
                RestConsumerContextPathMatcher.matchBestPath("GET", "/camel/a/b/3", consumerPaths);
        assertEquals("/camel/a/b/{c}", path.getConsumerPath());
    }

    @ParameterizedTest
    @MethodSource
    public void testRestConsumerContextPathMatcherTemplateParseSuccess(String consumerPath, String requestPath) {

        assertDoesNotThrow(() -> RestConsumerContextPathMatcher.register(consumerPath));

        List<RestConsumerContextPathMatcher.ConsumerPath<MockConsumerPath>> consumerPaths = new ArrayList<>();
        consumerPaths.add(new MockConsumerPath("GET", consumerPath));

        RestConsumerContextPathMatcher.ConsumerPath<?> path =
                RestConsumerContextPathMatcher.matchBestPath("GET", requestPath, consumerPaths);

        assertEquals(consumerPath, path.getConsumerPath());
    }

    private static Stream<Arguments> testRestConsumerContextPathMatcherTemplateParseSuccess() {
        return Stream.of(
                Arguments.of("/camel/{myparamname1}", "/camel/param"),
                Arguments.of("/camel/{myParamName1}", "/camel/param"),
                Arguments.of("/camel/{my_param_name1}", "/camel/param"),
                Arguments.of("/camel/{my-param-name1}", "/camel/param"),
                Arguments.of("/camel/{my-param_name1}", "/camel/param"),
                Arguments.of("/camel/{my-param_name1}/path-ab/{my-param_name2}", "/camel/param1/path-ab/param2"),
                Arguments.of(
                        "/camel/{my-param_name1}/path-ab/{my-param_name2}/*",
                        "/camel/param1/path-ab/param2/something"));
    }

    @Test
    public void testRestConsumerContextPathMatcherWithWildcard() {
        List<RestConsumerContextPathMatcher.ConsumerPath<MockConsumerPath>> consumerPaths = new ArrayList<>();
        consumerPaths.add(new MockConsumerPath("GET", "/camel/myapp/info"));
        consumerPaths.add(new MockConsumerPath("GET", "/camel/myapp/{id}"));
        consumerPaths.add(new MockConsumerPath("GET", "/camel/myapp/order/*"));

        RestConsumerContextPathMatcher.register("/camel/myapp/order/*");

        RestConsumerContextPathMatcher.ConsumerPath<?> path1 =
                RestConsumerContextPathMatcher.matchBestPath("GET", "/camel/myapp/info", consumerPaths);

        RestConsumerContextPathMatcher.ConsumerPath<?> path2 =
                RestConsumerContextPathMatcher.matchBestPath("GET", "/camel/myapp/1", consumerPaths);

        RestConsumerContextPathMatcher.ConsumerPath<?> path3 =
                RestConsumerContextPathMatcher.matchBestPath("GET", "/camel/myapp/order/foo", consumerPaths);

        assertEquals("/camel/myapp/info", path1.getConsumerPath());
        assertEquals("/camel/myapp/{id}", path2.getConsumerPath());
        assertEquals("/camel/myapp/order/*", path3.getConsumerPath());
    }

    @Test
    public void testRestConsumerContextPathMatcherOrder() {
        List<RestConsumerContextPathMatcher.ConsumerPath<MockConsumerPath>> consumerPaths = new ArrayList<>();
        consumerPaths.add(new MockConsumerPath("GET", "/camel/*"));
        consumerPaths.add(new MockConsumerPath("GET", "/camel/foo"));
        consumerPaths.add(new MockConsumerPath("GET", "/camel/foo/{id}"));

        RestConsumerContextPathMatcher.register("/camel/*");

        RestConsumerContextPathMatcher.ConsumerPath<?> path1 =
                RestConsumerContextPathMatcher.matchBestPath("GET", "/camel/foo", consumerPaths);

        RestConsumerContextPathMatcher.ConsumerPath<?> path2 =
                RestConsumerContextPathMatcher.matchBestPath("GET", "/camel/foo/bar", consumerPaths);

        RestConsumerContextPathMatcher.ConsumerPath<?> path3 =
                RestConsumerContextPathMatcher.matchBestPath("GET", "/camel/foo/bar/1", consumerPaths);

        assertEquals("/camel/foo", path1.getConsumerPath());
        assertEquals("/camel/foo/{id}", path2.getConsumerPath());
        assertEquals("/camel/*", path3.getConsumerPath());
    }

    @Test
    public void testRestConsumerContextPathMatcherPetStore() {
        final List<RestConsumerContextPathMatcher.ConsumerPath<MockConsumerPath>> consumerPaths = createConsumerPaths();

        RestConsumerContextPathMatcher.register("/api/v3/*");

        RestConsumerContextPathMatcher.ConsumerPath<?> path1 =
                RestConsumerContextPathMatcher.matchBestPath("GET", "/pet", consumerPaths);
        assertNull(path1);
        RestConsumerContextPathMatcher.ConsumerPath<?> path2 =
                RestConsumerContextPathMatcher.matchBestPath("POST", "/pet", consumerPaths);
        assertEquals("/pet", path2.getConsumerPath());

        RestConsumerContextPathMatcher.ConsumerPath<?> path3 =
                RestConsumerContextPathMatcher.matchBestPath("GET", "/pet/findByStatus", consumerPaths);
        assertEquals("/pet/findByStatus", path3.getConsumerPath());
        RestConsumerContextPathMatcher.ConsumerPath<?> path4 =
                RestConsumerContextPathMatcher.matchBestPath("DELETE", "/pet/findByStatus", consumerPaths);
        assertNull(path4);

        RestConsumerContextPathMatcher.ConsumerPath<?> path5 =
                RestConsumerContextPathMatcher.matchBestPath("GET", "/pet/findByTags", consumerPaths);
        assertEquals("/pet/findByTags", path5.getConsumerPath());
        RestConsumerContextPathMatcher.ConsumerPath<?> path6 =
                RestConsumerContextPathMatcher.matchBestPath("POST", "/pet/findByStatus", consumerPaths);
        assertNull(path6);

        RestConsumerContextPathMatcher.ConsumerPath<?> path7 =
                RestConsumerContextPathMatcher.matchBestPath("GET", "/pet/123", consumerPaths);
        assertEquals("/pet/{petId}", path7.getConsumerPath());
        RestConsumerContextPathMatcher.ConsumerPath<?> path8 =
                RestConsumerContextPathMatcher.matchBestPath("POST", "/pet/222", consumerPaths);
        assertEquals("/pet/{petId}", path8.getConsumerPath());
        RestConsumerContextPathMatcher.ConsumerPath<?> path9 =
                RestConsumerContextPathMatcher.matchBestPath("DELETE", "/pet/333", consumerPaths);
        assertEquals("/pet/{petId}", path9.getConsumerPath());
        RestConsumerContextPathMatcher.ConsumerPath<?> path10 =
                RestConsumerContextPathMatcher.matchBestPath("PUT", "/pet/444", consumerPaths);
        assertNull(path10);

        RestConsumerContextPathMatcher.ConsumerPath<?> path11 =
                RestConsumerContextPathMatcher.matchBestPath("POST", "/pet/123/uploadImage", consumerPaths);
        assertEquals("/pet/{petId}/uploadImage", path11.getConsumerPath());
        RestConsumerContextPathMatcher.ConsumerPath<?> path12 =
                RestConsumerContextPathMatcher.matchBestPath("DELETE", "/pet/222/uploadImage", consumerPaths);
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
