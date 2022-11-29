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
package org.apache.camel.language;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.LanguageBuilderFactory;
import org.apache.camel.component.jackson.JacksonConstants;
import org.apache.camel.model.language.JqExpression;

/**
 * Ensures that the "jq" language is compliant with the single input / typed language expectations.
 */
class JqLanguageTest extends AbstractSingleInputTypedLanguageTest<JqExpression.Builder, JqExpression> {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    JqLanguageTest() {
        super(".foo", LanguageBuilderFactory::jq);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext answer = super.createCamelContext();
        answer.getGlobalOptions().put(JacksonConstants.ENABLE_TYPE_CONVERTER, "true");
        answer.getGlobalOptions().put(JacksonConstants.TYPE_CONVERTER_TO_POJO, "true");
        return answer;
    }

    @Override
    protected Object defaultContentToSend() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("foo", "1");
        return node;
    }

    @Override
    protected TestContext testWithoutTypeContext() {
        return new TestContext(defaultContentToSend(), new TextNode("1"), TextNode.class);
    }
}
