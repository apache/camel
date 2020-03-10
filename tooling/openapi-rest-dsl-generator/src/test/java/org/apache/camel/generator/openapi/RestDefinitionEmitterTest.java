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
package org.apache.camel.generator.openapi;

import java.io.IOException;
import java.util.List;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestOperationParamDefinition;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RestDefinitionEmitterTest {
    @Test
    public void shouldGenerateObjects() throws IOException {
        try (DefaultCamelContext context = new DefaultCamelContext()) {

            final RestDefinitionEmitter emitter = new RestDefinitionEmitter(context);

            emitter.emit("rest");
            emitter.emit("put", "/pet");
            emitter.emit("consumes", new Object[] {new String[] {"application/json", "application/xml"}});
            emitter.emit("produces", new Object[] {new String[] {"application/xml", "application/json"}});
            emitter.emit("param");
            emitter.emit("name", "body");
            emitter.emit("type", RestParamType.body);
            emitter.emit("required", true);
            emitter.emit("endParam");

            final RestsDefinition result = emitter.result();
            final List<RestDefinition> rests = result.getRests();
            assertThat(rests).hasSize(1);

            final RestDefinition rest = rests.get(0);
            final List<VerbDefinition> verbs = rest.getVerbs();
            assertThat(verbs).hasSize(1);

            final VerbDefinition definition = verbs.get(0);
            assertThat(definition.asVerb()).isEqualTo("put");
            assertThat(definition.getUri()).isEqualTo("/pet");
            assertThat(definition.getConsumes()).isEqualTo("application/json,application/xml");
            assertThat(definition.getProduces()).isEqualTo("application/xml,application/json");

            final List<RestOperationParamDefinition> params = definition.getParams();
            assertThat(params).hasSize(1);

            final RestOperationParamDefinition param = params.get(0);
            assertThat(param.getName()).isEqualTo("body");
            assertThat(param.getType()).isEqualTo(RestParamType.body);
            assertThat(param.getRequired()).isEqualTo(true);
        }
    }
}
