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
package org.apache.camel.generator.swagger;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.MethodSpec.Builder;

import org.apache.camel.model.rest.RestParamType;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodBodySourceCodeEmitterTest {

    @Test
    public void shouldGenerateSourceCode() {
        final Builder method = MethodSpec.methodBuilder("configure");

        final MethodBodySourceCodeEmitter emitter = new MethodBodySourceCodeEmitter(method);

        emitter.emit("rest");
        emitter.emit("put", "/pet");
        emitter.emit("consumes", new Object[] {new String[] {"application/json", "application/xml"}});
        emitter.emit("produces", new Object[] {new String[] {"application/xml", "application/json"}});
        emitter.emit("param");
        emitter.emit("name", "body");
        emitter.emit("type", RestParamType.body);
        emitter.emit("required", true);
        emitter.emit("endParam");

        assertThat(emitter.result().toString()).isEqualTo("void configure() {\n"//
            + "  rest()\n"//
            + "    .put(\"/pet\")\n"//
            + "      .consumes(\"application/json,application/xml\")\n"//
            + "      .produces(\"application/xml,application/json\")\n"//
            + "      .param()\n"//
            + "        .name(\"body\")\n"//
            + "        .type(org.apache.camel.model.rest.RestParamType.body)\n"//
            + "        .required(true)\n"//
            + "      .endParam();\n"//
            + "}\n");
    }
}
