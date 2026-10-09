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

import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.Model;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteConfigurationsDefinition;
import org.apache.camel.yaml.LwModelToYAMLDumper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteConfigurationYamlDumpTest extends YamlTestSupport {

    @Test
    void routeConfigurationIsDumpedAsTheYamlDslLoadsIt() throws Exception {
        loadRoutes("""
                - routeConfiguration:
                    id: myConfig
                    onException:
                      - onException:
                          exception:
                            - java.lang.Exception
                          handled:
                            constant: "true"
                          steps:
                            - to: mock:error
                    onCompletion:
                      - onCompletion:
                          steps:
                            - to: mock:completed
                    interceptSendToEndpoint:
                      - interceptSendToEndpoint:
                          uri: "mock:result*"
                          steps:
                            - to: mock:send
                    interceptFrom:
                      - interceptFrom:
                          uri: "direct*"
                          steps:
                            - to: mock:from
                    intercept:
                      - intercept:
                          steps:
                            - to: mock:intercept
                """);
        Model model = context.getCamelContextExtension().getContextPlugin(Model.class);

        // as the route configurations are dumped by camel.main.dumpRoutes, which camel validate normalize uses
        RouteConfigurationsDefinition configs = new RouteConfigurationsDefinition();
        configs.getRouteConfigurations().addAll(model.getRouteConfigurationDefinitions());
        String yaml = new LwModelToYAMLDumper().dumpModelAsYaml(context, configs, false, true, false, false);

        // the dump is valid for the YAML DSL schema and loads with the same content
        model.removeRouteConfiguration(model.getRouteConfigurationDefinition("myConfig"));
        loadRoutes(yaml);
        RouteConfigurationDefinition config = model.getRouteConfigurationDefinition("myConfig");
        assertThat(config).isNotNull();
        assertThat(config.getOnExceptions()).singleElement()
                .satisfies(oe -> assertThat(oe.getExceptions()).containsExactly("java.lang.Exception"));
        assertThat(config.getOnCompletions()).hasSize(1);
        assertThat(config.getInterceptSendTos()).singleElement()
                .satisfies(i -> assertThat(i.getUri()).isEqualTo("mock:result*"));
        assertThat(config.getInterceptFroms()).singleElement()
                .satisfies(i -> assertThat(i.getUri()).isEqualTo("direct*"));
        assertThat(config.getIntercepts()).hasSize(1);
    }
}
