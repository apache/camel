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
package org.apache.camel.component.grok;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class GrokPatternsTest extends CamelTestSupport {

    public static List<Arguments> data() {
        String randomUuid = UUID.randomUUID().toString();
        return Arrays.asList(
                Arguments.of("%{QS:qs}", "this is some \"quoted string\".", test("qs", "quoted string")),
                Arguments.of("%{UUID:uuid}", "some " + randomUuid, test("uuid", randomUuid)),
                Arguments.of("%{MAC:mac}", "some:invalid:prefix:of:eth0:02:00:4c:4f:4f:50", test("mac", "02:00:4c:4f:4f:50")),
                Arguments.of("%{PATH:path}", "C:\\path\\file", test("path", "C:\\path\\file")),
                Arguments.of("%{PATH:path}", "C:\\path\\file.txt", test("path", "C:\\path\\file.txt")),
                Arguments.of("%{PATH:path}", "\\\\server\\share\\path\\file", test("path", "\\\\server\\share\\path\\file")),
                Arguments.of("%{PATH:path}", "/root/.hidden_file", test("path", "/root/.hidden_file")),
                Arguments.of("%{PATH:path}", "/home/user/../../mnt", test("path", "/home/user/../../mnt")),
                Arguments.of("%{PATH:path}", "/root", test("path", "/root")),
                Arguments.of("%{URI:camelSite}", "the site is at http://camel.apache.org/",
                        test("camelSite", "http://camel.apache.org/")),
                Arguments.of("%{URI:camelSite}", "the dataformat docs is at http://camel.apache.org/data-format.html",
                        test("camelSite", "http://camel.apache.org/data-format.html")),
                Arguments.of("%{NUMBER:num}", "number is 123.", test("num", "123")),
                Arguments.of("%{NUMBER:num:integer}", "number is 123.", test("num", 123)),
                Arguments.of("%{IP:ip}", "my ip is 192.168.0.1", test("ip", "192.168.0.1")),
                Arguments.of("%{TIMESTAMP_ISO8601:timestamp}", "This test was created at 2019-05-26T10:54:15Z test plain",
                        test("timestamp", "2019-05-26T10:54:15Z")),
                Arguments.of("%{TIMESTAMP_ISO8601:timestamp:date}",
                        "This test was created at 2019-05-26T10:54:15Z test convert",
                        test("timestamp", Instant.ofEpochSecond(1558868055))));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
            }
        };
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testPattern(String pattern, String input, Consumer<Map> expectedOutputTest) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:input")
                        .unmarshal().grok(pattern);
            }
        });

        assertDoesNotThrow(() -> expectedOutputTest.accept(
                template.requestBody("direct:input", input, Map.class)));
    }

    private static Consumer<Map> test(String key, Object value) {
        return new Consumer<Map>() {
            @Override
            public void accept(Map m) {
                boolean result = m != null && m.containsKey(key) && Objects.equals(m.get(key), value);
                assertTrue(result, String.format("Expected: map.get(%s) == %s. Given map %s", key, value, m));
            }

            @Override
            public String toString() {
                return String.format("map[%s] = %s", key, value);
            }
        };
    }

}
