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
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.LogDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LineNumberTest extends YamlTestSupport {

    @Test
    void lineNumberDefinition() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - log:
                         loggingLevel: "ERROR"
                         message: "test"
                         logName: "yaml"
                      - to: "direct:result"
                """);
        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);

        var from = (FromDefinition) context.getRouteDefinitions().get(0).getInput();
        assertThat(from.getUri()).isEqualTo("direct:start");
        assertThat(from.getLineNumber()).isEqualTo(1);

        var log = (LogDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(log.getLoggingLevel()).isEqualTo("ERROR");
        assertThat(log.getMessage()).isEqualTo("test");
        assertThat(log.getLogName()).isEqualTo("yaml");
        assertThat(log.getLineNumber()).isEqualTo(4);

        var to = (ToDefinition) context.getRouteDefinitions().get(0).getOutputs().get(1);
        assertThat(to.getUri()).isEqualTo("direct:result");
        assertThat(to.getLineNumber()).isEqualTo(8);
    }

    @Test
    void lineNumberRoute() throws Exception {
        loadRoutes("""
                - route:
                    from:
                      uri: "direct:info"
                      steps:
                        - log: "message"
                """);
        assertThat(context.getRouteDefinitions()).hasSize(1);

        var route = (RouteDefinition) context.getRouteDefinitions().get(0);
        assertThat(route.getInput().getLineNumber()).isEqualTo(3);
        assertThat(route.getInput().getEndpointUri()).isEqualTo("direct:info");

        var log = (LogDefinition) route.getOutputs().get(0);
        assertThat(log.getLineNumber()).isEqualTo(5);
        assertThat(log.getMessage()).isEqualTo("message");
    }

    @Test
    void lineNumberFile() throws Exception {
        String rloc = "classpath:/stuff/my-route.yaml";
        Resource rdsl = PluginHelper.getResourceLoader(context).resolveResource(rloc);
        PluginHelper.getRoutesLoader(context).loadRoutes(rdsl);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);

        var from = (FromDefinition) context.getRouteDefinitions().get(0).getInput();
        assertThat(from.getUri()).isEqualTo("quartz:foo?cron={{myCron}}");
        assertThat(from.getLineNumber()).isEqualTo(21);

        var log0 = (LogDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(log0.getMessage()).isEqualTo("Start");
        assertThat(log0.getLineNumber()).isEqualTo(23);

        var to1 = (ToDefinition) context.getRouteDefinitions().get(0).getOutputs().get(1);
        assertThat(to1.getUri()).isEqualTo("bean:myBean?method=hello");
        assertThat(to1.getLineNumber()).isEqualTo(24);

        var to3 = (ToDefinition) context.getRouteDefinitions().get(0).getOutputs().get(3);
        assertThat(to3.getUri()).isEqualTo("bean:myBean?method=bye");
        assertThat(to3.getLineNumber()).isEqualTo(26);

        var log4 = (LogDefinition) context.getRouteDefinitions().get(0).getOutputs().get(4);
        assertThat(log4.getMessage()).isEqualTo("${body}");
        assertThat(log4.getLineNumber()).isEqualTo(27);

        var choice5 = (ChoiceDefinition) context.getRouteDefinitions().get(0).getOutputs().get(5);
        assertThat(choice5.getLineNumber()).isEqualTo(28);

        var log6 = (LogDefinition) context.getRouteDefinitions().get(0).getOutputs().get(6);
        assertThat(log6.getMessage()).isEqualTo("${header.textProp}");
        assertThat(log6.getLineNumber()).isEqualTo(40);
    }

    @Test
    void lineNumberFileWithComments() throws Exception {
        String rloc = "classpath:/stuff/my-route-comment.yaml";
        Resource rdsl = PluginHelper.getResourceLoader(context).resolveResource(rloc);
        PluginHelper.getRoutesLoader(context).loadRoutes(rdsl);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);

        var from = (FromDefinition) context.getRouteDefinitions().get(0).getInput();
        assertThat(from.getUri()).isEqualTo("quartz:foo?cron={{myCron}}");
        assertThat(from.getLineNumber()).isEqualTo(23);

        var log0 = (LogDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(log0.getMessage()).isEqualTo("Start");
        assertThat(log0.getLineNumber()).isEqualTo(26);

        var to1 = (ToDefinition) context.getRouteDefinitions().get(0).getOutputs().get(1);
        assertThat(to1.getUri()).isEqualTo("bean:myBean?method=hello");
        assertThat(to1.getLineNumber()).isEqualTo(27);

        var to3 = (ToDefinition) context.getRouteDefinitions().get(0).getOutputs().get(3);
        assertThat(to3.getUri()).isEqualTo("bean:myBean?method=bye");
        assertThat(to3.getLineNumber()).isEqualTo(31);

        var log4 = (LogDefinition) context.getRouteDefinitions().get(0).getOutputs().get(4);
        assertThat(log4.getMessage()).isEqualTo("${body}");
        assertThat(log4.getLineNumber()).isEqualTo(32);

        var choice5 = (ChoiceDefinition) context.getRouteDefinitions().get(0).getOutputs().get(5);
        assertThat(choice5.getLineNumber()).isEqualTo(34); // TODO: should be 32

        var log6 = (LogDefinition) context.getRouteDefinitions().get(0).getOutputs().get(6);
        assertThat(log6.getMessage()).isEqualTo("${header.textProp}");
        assertThat(log6.getLineNumber()).isEqualTo(50);
    }
}
