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
package org.apache.camel.component.undertow;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A matchOnUriPrefix route of a base path declared after the route of a path template under it: it starts (it was
 * refused with "Duplicate handlers on a path", while the other order worked), answers the base path and what no
 * template matches, and starts again while the template route runs.
 */
class UndertowPrefixPathAfterTemplatePathTest extends BaseUndertowTest {

    @Test
    void prefixRouteDeclaredAfterTheTemplateRoute() throws Exception {
        // the template route starts first: the matchOnUriPrefix route of its base path starts too (it was refused)
        assertResponse(getPort(), "/goods", 200, "goods");
        assertResponse(getPort(), "/goods/a/b", 200, "goods");
        assertResponse(getPort(), "/goods/7", 200, "good 7");

        // and starts again while the template route runs
        context.getRouteController().stopRoute("goods");
        assertResponse(getPort(), "/goods/a/b", 404, null);
        context.getRouteController().startRoute("goods");
        assertResponse(getPort(), "/goods/a/b", 200, "goods");

        context.getRouteController().stopRoute("good");
        assertResponse(getPort(), "/goods/7", 200, "goods");
        assertResponse(getPort(), "/goods", 200, "goods");
    }

    private void assertResponse(int port, String path, int code, String body) {
        Exchange out = template.request("undertow:http://localhost:" + port + path + "?throwExceptionOnFailure=false", null);
        assertEquals(code, out.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class), path);
        if (body != null) {
            assertEquals(body, out.getMessage().getBody(String.class), path);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // the template path starts first, then the prefix path
                from("undertow:http://localhost:{{port}}/goods/{id}").routeId("good")
                        .setBody(simple("good ${header.id}"));
                from("undertow:http://localhost:{{port}}/goods?matchOnUriPrefix=true").routeId("goods")
                        .setBody(constant("goods"));
            }
        };
    }
}
