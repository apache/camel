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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultPackageScanResourceResolverTest {
    @Test
    public void testFileResourcesScan() {
        DefaultCamelContext ctx = new DefaultCamelContext(false);

        assertThat(loadRouteIDs(ctx, "file:src/test/resources/org/apache/camel/impl/engine/**/*.xml")).containsOnly("dummy-a", "scan-a", "dummy-b", "scan-b");
        assertThat(loadRouteIDs(ctx, "file:src/test/resources/org/apache/camel/impl/engine/a?/*.xml")).containsOnly("dummy-a", "scan-a");
        assertThat(loadRouteIDs(ctx, "file:src/test/resources/org/apache/camel/impl/engine/b?/*.xml")).containsOnly("dummy-b", "scan-b");
        assertThat(loadRouteIDs(ctx, "file:src/test/resources/org/apache/camel/impl/engine/c?/*.xml")).isEmpty();
    }

    private static Set<String> loadRouteIDs(CamelContext context, String path) {
        return loadRouteDefinitions(context, path).stream().map(RouteDefinition::getId).collect(Collectors.toSet());
    }

    private static List<RouteDefinition> loadRouteDefinitions(CamelContext context, String path) {
        List<RouteDefinition> answer = new ArrayList<>();

        ExtendedCamelContext ecc = context.adapt(ExtendedCamelContext.class);
        try {
            for (InputStream is : ecc.getPackageScanResourceResolver().findResources(path)) {
                RoutesDefinition routes = (RoutesDefinition) ecc.getXMLRoutesDefinitionLoader().loadRoutesDefinition(ecc, is);
                if (routes != null) {
                    answer.addAll(routes.getRoutes());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return answer;
    }
}
