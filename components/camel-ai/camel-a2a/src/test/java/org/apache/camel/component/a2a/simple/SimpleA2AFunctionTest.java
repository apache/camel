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
package org.apache.camel.component.a2a.simple;

import org.apache.camel.Expression;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleA2AFunctionTest {

    private final SimpleA2AFunction factory = new SimpleA2AFunction();
    private final DefaultCamelContext context = new DefaultCamelContext();

    @Test
    void emitSingleArgReturnsExpression() {
        Expression expr = factory.createFunction(context, "a2a:emit(processing now)", 0);
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).contains("a2a:emit(processing now)");
    }

    @Test
    void emitTwoArgsReturnsExpression() {
        Expression expr = factory.createFunction(context, "a2a:emit(INPUT_REQUIRED, need info)", 0);
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).contains("INPUT_REQUIRED");
        assertThat(expr.toString()).contains("need info");
    }

    @Test
    void emitEmptyArgsReturnsNull() {
        Expression expr = factory.createFunction(context, "a2a:emit()", 0);
        assertThat(expr).isNull();
    }

    @Test
    void textNoArgReturnsExpression() {
        Expression expr = factory.createFunction(context, "a2a:text", 0);
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).isEqualTo("a2a:text");
    }

    @Test
    void textWithArgReturnsExpression() {
        Expression expr = factory.createFunction(context, "a2a:text(${body})", 0);
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).contains("a2a:text");
    }

    @Test
    void dataNoArgReturnsExpression() {
        Expression expr = factory.createFunction(context, "a2a:data", 0);
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).isEqualTo("a2a:data");
    }

    @Test
    void dataWithArgReturnsExpression() {
        Expression expr = factory.createFunction(context, "a2a:data(${body})", 0);
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).contains("a2a:data");
    }

    @Test
    void fileNoArgReturnsExpression() {
        Expression expr = factory.createFunction(context, "a2a:file", 0);
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).isEqualTo("a2a:file");
    }

    @Test
    void fileWithArgReturnsExpression() {
        Expression expr = factory.createFunction(context, "a2a:file(${body})", 0);
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).contains("a2a:file");
    }

    @Test
    void unknownFunctionReturnsNull() {
        Expression expr = factory.createFunction(context, "a2a:unknown", 0);
        assertThat(expr).isNull();
    }

    @Test
    void nonA2AFunctionReturnsNull() {
        Expression expr = factory.createFunction(context, "something:else", 0);
        assertThat(expr).isNull();
    }

    @Test
    void cardReturnsExpression() {
        Expression expr = factory.createFunction(context, "a2a:card", 0);
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).isEqualTo("a2a:card");
    }

    @Test
    void cardNameReturnsExpression() {
        Expression expr = factory.createFunction(context, "a2a:card.name", 0);
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).isEqualTo("a2a:card.name");
    }

    @Test
    void cardDescriptionReturnsExpression() {
        Expression expr = factory.createFunction(context, "a2a:card.description", 0);
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).isEqualTo("a2a:card.description");
    }

    @Test
    void cardVersionReturnsExpression() {
        Expression expr = factory.createFunction(context, "a2a:card.version", 0);
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).isEqualTo("a2a:card.version");
    }

    @Test
    void cardSkillsReturnsExpression() {
        Expression expr = factory.createFunction(context, "a2a:card.skills", 0);
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).isEqualTo("a2a:card.skills");
    }

    @Test
    void cardSkillsJsonReturnsExpression() {
        Expression expr = factory.createFunction(context, "a2a:card.skills.json", 0);
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).isEqualTo("a2a:card.skills.json");
    }

    @Test
    void cardUrlReturnsExpression() {
        Expression expr = factory.createFunction(context, "a2a:card.url", 0);
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).isEqualTo("a2a:card.url");
    }
}
