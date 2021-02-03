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
package org.apache.camel.impl.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.Resource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultPackageScanResourceResolverTest {
    private static Set<String> loadRouteIDs(String path) throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext(false);
        for (RoutesBuilder builder : loadRouteDefinitions(ctx, path)) {
            ctx.addRoutes(builder);
        }
        return ctx.getRouteDefinitions().stream().map(RouteDefinition::getId).collect(Collectors.toSet());
    }

    private static List<RoutesBuilder> loadRouteDefinitions(CamelContext context, String path) {
        List<RoutesBuilder> answer = new ArrayList<>();

        ExtendedCamelContext ecc = context.adapt(ExtendedCamelContext.class);
        try {
            for (Resource resource : ecc.getPackageScanResourceResolver().findResources(path)) {
                answer.addAll(
                        ecc.getRoutesLoader().findRoutesBuilders(resource));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return answer;
    }

    @Test
    public void testFileResourcesScan() throws Exception {

        assertThat(loadRouteIDs("file:src/test/resources/org/apache/camel/impl/engine/**/*.xml"))
                .containsOnly("dummy-a", "scan-a", "dummy-b", "scan-b");
        assertThat(loadRouteIDs("file:src/test/resources/org/apache/camel/impl/engine/a?/*.xml"))
                .containsOnly("dummy-a", "scan-a");
        assertThat(loadRouteIDs("file:src/test/resources/org/apache/camel/impl/engine/b?/*.xml"))
                .containsOnly("dummy-b", "scan-b");
        assertThat(loadRouteIDs("file:src/test/resources/org/apache/camel/impl/engine/c?/*.xml"))
                .isEmpty();
    }
}
