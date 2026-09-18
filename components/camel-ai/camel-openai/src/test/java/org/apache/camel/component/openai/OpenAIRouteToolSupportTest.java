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
package org.apache.camel.component.openai;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.tool.AiToolAnnotations;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIRouteToolSupportTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-tool:lookup"
                     + "?tags=support"
                     + "&description=Lookup data"
                     + "&returnDirect=true")
                        .setBody(constant("ok"));

                from("ai-tool:other"
                     + "?tags=support"
                     + "&description=Other tool")
                        .setBody(constant("other"));
            }
        };
    }

    @Test
    void discoversToolsByTagAndDetectsReturnDirect() {
        var tools = OpenAIRouteToolSupport.discoverRouteTools(context, "support");

        assertThat(tools).containsKeys("lookup", "other");
        assertThat(OpenAIRouteToolSupport.returnDirectToolNames(tools)).containsExactly("lookup");
        assertThat(OpenAIRouteToolSupport.toOpenAiTools(tools)).hasSize(2);
    }

    @Test
    void isReturnDirectUsesAnnotationsRecord() {
        AiToolAnnotations annotations = new AiToolAnnotations(null, null, null, null, null, true);

        assertThat(OpenAIRouteToolSupport.isReturnDirect(annotations)).isTrue();
        assertThat(OpenAIRouteToolSupport.isReturnDirect(null)).isFalse();
    }
}
