/**
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
package org.apache.camel.spring.boot;

import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class CamelAutoConfigurationWithContextRunnerTest {
    @Test
    public void testCamelAnnotationsAutoConfigurationBean() {
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                        CamelAutoConfigurationTest.class,
                        RouteConfigWithCamelContextInjected.class,
                        CamelAutoConfigurationWithContextRunnerTest.class
                )
            )
            .withPropertyValues(
                    "camel.springboot.consumerTemplateCacheSize=100",
                    "camel.springboot.jmxEnabled=true",
                    "camel.springboot.name=customName",
                    "camel.springboot.typeConversion=true",
                    "camel.springboot.threadNamePattern=customThreadName #counter#"
            )
            .run((context) -> {
                    assertThat(context).doesNotHaveBean(CamelAnnotationsTest.TestConfig.class);
                    assertThat(context).doesNotHaveBean(CamelAnnotationsTest.class);
                    assertThat(context).getBeanNames(CamelAutoConfigurationTest.class).hasSize(1);
                    assertThat(context).hasSingleBean(RouteConfigWithCamelContextInjected.class);
                    assertThat(context).getBean(CamelAutoConfigurationTest.class).hasNoNullFieldsOrProperties();
                                       
                }
            );
    }

}
