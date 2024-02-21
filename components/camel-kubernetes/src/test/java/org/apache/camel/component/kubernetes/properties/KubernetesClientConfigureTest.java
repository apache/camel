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
package org.apache.camel.component.kubernetes.properties;

import java.util.Map;

import io.fabric8.kubernetes.client.ConfigBuilder;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class KubernetesClientConfigureTest {

    @Test
    public void testConfigure() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.start();

        ConfigBuilder config = new ConfigBuilder();

        PropertyConfigurer configurer = PluginHelper.getConfigurerResolver(context)
                .resolvePropertyConfigurer(ConfigBuilder.class.getName(), context);
        Assertions.assertNotNull(configurer, "Cannot find generated configurer");

        PropertyBindingSupport.build()
                .withProperties(Map.of("masterUrl", "http://localhost:1234"))
                .withFluentBuilder(true)
                .withIgnoreCase(true)
                .withConfigurer(configurer)
                .withTarget(config)
                .withCamelContext(context)
                .withRemoveParameters(false)
                .bind();

        Assertions.assertEquals("http://localhost:1234", config.getMasterUrl());
    }
}
