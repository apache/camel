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
package org.apache.camel.dsl.yaml;

import java.io.IOException;

import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmptyRouteFileTest extends YamlTestSupport {

    @Test
    void emptyFile() {
        RoutesLoader loader = PluginHelper.getRoutesLoader(context);
        var res = ResourceHelper.fromString("dummy.yaml", "");
        var e = assertThrows(IOException.class, () -> loader.loadRoutes(res));
        assertThat(e.getMessage()).isEqualTo("Resource is empty: dummy.yaml");
    }
}
