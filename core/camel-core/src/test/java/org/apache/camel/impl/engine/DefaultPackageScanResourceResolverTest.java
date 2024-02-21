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

import java.io.File;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.PackageScanResourceResolver;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultPackageScanResourceResolverTest {
    @Test
    public void testFileResourcesScan() throws Exception {
        final DefaultCamelContext ctx = new DefaultCamelContext();
        final PackageScanResourceResolver resolver = PluginHelper.getPackageScanResourceResolver(ctx);

        assertThat(resolver.findResources("file:src/test/resources/org/apache/camel/impl/engine/**/*.xml"))
                .hasSize(4)
                .anyMatch(r -> r.getLocation().contains("ar" + File.separator + "camel-scan.xml"))
                .anyMatch(r -> r.getLocation().contains("ar" + File.separator + "camel-dummy.xml"))
                .anyMatch(r -> r.getLocation().contains("br" + File.separator + "camel-scan.xml"))
                .anyMatch(r -> r.getLocation().contains("br" + File.separator + "camel-dummy.xml"));
        assertThat(resolver.findResources("file:src/test/resources/org/apache/camel/impl/engine/a?/*.xml"))
                .hasSize(2)
                .anyMatch(r -> r.getLocation().contains("ar" + File.separator + "camel-scan.xml"))
                .anyMatch(r -> r.getLocation().contains("ar" + File.separator + "camel-dummy.xml"));
        assertThat(resolver.findResources("file:src/test/resources/org/apache/camel/impl/engine/b?/*.xml"))
                .hasSize(2)
                .anyMatch(r -> r.getLocation().contains("br" + File.separator + "camel-scan.xml"))
                .anyMatch(r -> r.getLocation().contains("br" + File.separator + "camel-dummy.xml"));
        assertThat(resolver.findResources("file:src/test/resources/org/apache/camel/impl/engine/c?/*.xml"))
                .isEmpty();
    }
}
