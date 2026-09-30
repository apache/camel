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

import org.apache.camel.dsl.yaml.support.MockRestConsumerFactory;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.dsl.yaml.support.model.MyBean;
import org.apache.camel.dsl.yaml.support.model.MyFooBar;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.rest.GetDefinition;
import org.apache.camel.model.rest.ParamDefinition;
import org.apache.camel.model.rest.PostDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RestTest extends YamlTestSupport {

    @Test
    void loadRestConfiguration() throws Exception {
        loadRoutes("""
                    - beans:
                      - name: myRestConsumerFactory
                        type: %s
                    - restConfiguration:
                        component: "servlet"
                        contextPath: "/foo"
                        dataFormatProperty:
                          - key: "contentTypeHeader"
                            value: "false"
                """.formatted(MockRestConsumerFactory.class.getName()));

        assertThat(context.getRestConfiguration().getComponent()).isEqualTo("servlet");
        assertThat(context.getRestConfiguration().getContextPath()).isEqualTo("/foo");
        assertThat(context.getRestConfiguration().getDataFormatProperties().get("contentTypeHeader")).isEqualTo("false");
    }

    @Test
    void loadRestTo() throws Exception {
        loadRoutes("""
                    - beans:
                      - name: myRestConsumerFactory
                        type: %s
                    - rest:
                        get:
                          - path: "/foo"
                            type: %s
                            outType: %s
                            to: "direct:bar"
                    - from:
                        uri: 'direct:bar'
                        steps:
                          - to: 'mock:bar'
                """.formatted(MockRestConsumerFactory.class.getName(), MyFooBar.class.getName(), MyBean.class.getName()));

        assertThat(context.getRestDefinitions().size()).isEqualTo(1);

        RestDefinition rest = context.getRestDefinitions().get(0);
        assertThat(rest.getVerbs().size()).isEqualTo(1);

        VerbDefinition verb = rest.getVerbs().get(0);
        assertThat(verb.getPath()).isEqualTo("/foo");
        assertThat(verb.getType()).isEqualTo(MyFooBar.class.getName());
        assertThat(verb.getOutType()).isEqualTo(MyBean.class.getName());

        assertThat(verb.getTo()).isInstanceOf(ToDefinition.class);
        ToDefinition to = (ToDefinition) verb.getTo();
        assertThat(to.getEndpointUri()).isEqualTo("direct:bar");
    }

    @Test
    void loadRestRoute() throws Exception {
        loadRoutes("""
                    - beans:
                      - name: myRestConsumerFactory
                        type: %s
                    - rest:
                        get:
                         -  path: "/foo"
                            type: %s
                            outType: %s
                            to: "direct:bar"
                    - from:
                        uri: 'direct:bar'
                        steps:
                          - to: 'mock:bar'
                """.formatted(MockRestConsumerFactory.class.getName(), MyFooBar.class.getName(), MyBean.class.getName()));

        assertThat(context.getRestDefinitions().size()).isEqualTo(1);

        RestDefinition rest = context.getRestDefinitions().get(0);
        assertThat(rest.getVerbs().size()).isEqualTo(1);

        VerbDefinition verb = rest.getVerbs().get(0);
        assertThat(verb.getPath()).isEqualTo("/foo");
        assertThat(verb.getType()).isEqualTo(MyFooBar.class.getName());
        assertThat(verb.getOutType()).isEqualTo(MyBean.class.getName());

        assertThat(verb.getTo()).isInstanceOf(ToDefinition.class);
        ToDefinition to = (ToDefinition) verb.getTo();
        assertThat(to.getEndpointUri()).isEqualTo("direct:bar");
    }

    @Test
    void loadRestVerbAlias() throws Exception {
        loadRoutes("""
                    - beans:
                      - name: myRestConsumerFactory
                        type: %s
                    - rest:
                        post:
                          - path: "/foo"
                            id: "foolish"
                            type: %s
                            outType: %s
                            to: "direct:foo"
                          - path: "/baz"
                            id: "bazzy"
                            to: "direct:baz"
                        get:
                          - path: "/getFoo"
                            to: "direct:getFoo"
                    - from:
                        uri: 'direct:bar'
                        steps:
                          - to: 'mock:bar'
                """.formatted(MockRestConsumerFactory.class.getName(), MyFooBar.class.getName(), MyBean.class.getName()));

        assertThat(context.getRestDefinitions().size()).isEqualTo(1);

        RestDefinition rest = context.getRestDefinitions().get(0);
        assertThat(rest.getVerbs().size()).isEqualTo(3);

        assertThat(rest.getVerbs().get(0)).isInstanceOf(PostDefinition.class);
        PostDefinition post1 = (PostDefinition) rest.getVerbs().get(0);
        assertThat(post1.getPath()).isEqualTo("/foo");
        assertThat(post1.getType()).isEqualTo(MyFooBar.class.getName());
        assertThat(post1.getOutType()).isEqualTo(MyBean.class.getName());
        assertThat(post1.getTo()).isInstanceOf(ToDefinition.class);
        ToDefinition to1 = (ToDefinition) post1.getTo();
        assertThat(to1.getEndpointUri()).isEqualTo("direct:foo");

        assertThat(rest.getVerbs().get(1)).isInstanceOf(PostDefinition.class);
        PostDefinition post2 = (PostDefinition) rest.getVerbs().get(1);
        assertThat(post2.getPath()).isEqualTo("/baz");
        assertThat(post2.getTo()).isInstanceOf(ToDefinition.class);
        ToDefinition to2 = (ToDefinition) post2.getTo();
        assertThat(to2.getEndpointUri()).isEqualTo("direct:baz");

        assertThat(rest.getVerbs().get(2)).isInstanceOf(GetDefinition.class);
        GetDefinition get = (GetDefinition) rest.getVerbs().get(2);
        assertThat(get.getPath()).isEqualTo("/getFoo");
        assertThat(get.getTo()).isInstanceOf(ToDefinition.class);
        ToDefinition to3 = (ToDefinition) get.getTo();
        assertThat(to3.getEndpointUri()).isEqualTo("direct:getFoo");
    }

    @Test
    void loadRestFull() throws Exception {
        String rloc = "classpath:/routes/rest-dsl.yaml";
        Resource rdsl = PluginHelper.getResourceLoader(context).resolveResource(rloc);
        loadRoutes(rdsl);

        assertThat(context.getRestDefinitions()).isNotNull();
        assertThat(context.getRestDefinitions().isEmpty()).isFalse();
    }

    @Test
    void loadRestGenerated() throws Exception {
        String rloc = "classpath:/rest-dsl/generated-rest-dsl.yaml";
        Resource rdsl = PluginHelper.getResourceLoader(context).resolveResource(rloc);
        loadRoutes(rdsl);

        assertThat(context.getRestDefinitions()).isNotNull();
        assertThat(context.getRestDefinitions().isEmpty()).isFalse();
    }

    @Test
    void loadRestAllowableValues() throws Exception {
        String rloc = "classpath:/routes/rest-allowable-values-dsl.yaml";
        Resource rdsl = PluginHelper.getResourceLoader(context).resolveResource(rloc);
        loadRoutes(rdsl);

        assertThat(context.getRestDefinitions()).isNotNull();
        assertThat(context.getRestDefinitions().isEmpty()).isFalse();

        ParamDefinition param = context.getRestDefinitions().get(0).getVerbs().get(0).getParams().get(0);
        assertThat(param.getAllowableValues().size()).isEqualTo(3);
        assertThat(param.getAllowableValues().get(0).getValue()).isEqualTo("available");
        assertThat(param.getAllowableValues().get(1).getValue()).isEqualTo("pending");
        assertThat(param.getAllowableValues().get(2).getValue()).isEqualTo("sold");
    }

    @Test
    void loadRestEnableCORS() throws Exception {
        loadRoutes("""
                    - rest:
                        get:
                          - path: "/foo"
                            type: %s
                            outType: %s
                            enableCORS: true
                            to: "direct:bar"
                    - from:
                        uri: 'direct:bar'
                        steps:
                          - to: 'mock:bar'
                """.formatted(MyFooBar.class.getName(), MyBean.class.getName()));

        assertThat(context.getRestDefinitions().size()).isEqualTo(1);

        RestDefinition rest = context.getRestDefinitions().get(0);
        assertThat(rest.getVerbs().size()).isEqualTo(1);

        VerbDefinition verb = rest.getVerbs().get(0);
        assertThat(verb.getPath()).isEqualTo("/foo");
        assertThat(verb.getType()).isEqualTo(MyFooBar.class.getName());
        assertThat(verb.getOutType()).isEqualTo(MyBean.class.getName());
        assertThat(verb.getEnableCORS()).isEqualTo("true");

        assertThat(verb.getTo()).isInstanceOf(ToDefinition.class);
        ToDefinition to = (ToDefinition) verb.getTo();
        assertThat(to.getEndpointUri()).isEqualTo("direct:bar");
    }
}
