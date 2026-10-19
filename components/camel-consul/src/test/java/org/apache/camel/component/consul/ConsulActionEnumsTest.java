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
package org.apache.camel.component.consul;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.component.consul.endpoint.ConsulAgentActions;
import org.apache.camel.component.consul.endpoint.ConsulCatalogActions;
import org.apache.camel.component.consul.endpoint.ConsulCoordinatesActions;
import org.apache.camel.component.consul.endpoint.ConsulEventActions;
import org.apache.camel.component.consul.endpoint.ConsulHealthActions;
import org.apache.camel.component.consul.endpoint.ConsulKeyValueActions;
import org.apache.camel.component.consul.endpoint.ConsulPreparedQueryActions;
import org.apache.camel.component.consul.endpoint.ConsulSessionActions;
import org.apache.camel.component.consul.endpoint.ConsulStatusActions;
import org.apache.camel.spi.UriParam;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The action option lists the actions of every API, so the catalog and the tools that validate routes with it accept
 * consul:kv?action=PUT. It listed only the agent actions.
 */
public class ConsulActionEnumsTest {

    @Test
    public void theActionOptionHasTheActionsOfEveryApi() throws Exception {
        Set<String> actions = new TreeSet<>();
        for (Class<?> api : List.of(ConsulAgentActions.class, ConsulCatalogActions.class, ConsulCoordinatesActions.class,
                ConsulEventActions.class, ConsulHealthActions.class, ConsulKeyValueActions.class,
                ConsulPreparedQueryActions.class, ConsulSessionActions.class, ConsulStatusActions.class)) {
            for (Field field : api.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                    actions.add((String) field.get(null));
                }
            }
        }
        String enums = ConsulConfiguration.class.getDeclaredField("action").getAnnotation(UriParam.class).enums();

        assertThat(actions).hasSize(35);
        assertThat(Set.of(enums.split(","))).isEqualTo(actions);
    }
}
