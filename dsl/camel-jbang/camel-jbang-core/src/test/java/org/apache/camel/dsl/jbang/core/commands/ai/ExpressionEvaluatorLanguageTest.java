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
package org.apache.camel.dsl.jbang.core.commands.ai;

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24907: a language that is not on this process's classpath is downloaded, so jsonpath, jq and the others can be
 * evaluated before an integration runs. The download needs a network or a filled local repository, so the test that
 * downloads runs with -Dcamel.test.download=true.
 */
class ExpressionEvaluatorLanguageTest {

    private static final String STOCK = "[{\"sku\":\"CAMEL-MUG\",\"qty\":42},{\"sku\":\"CAMEL-CAP\",\"qty\":0}]";

    @Test
    void aLanguageThatCannotBeLoadedSaysWhatToDo() {
        JsonObject result = ExpressionEvaluator.evaluate(new ToolContext(), "no-such-language", "x", null);

        assertThat(result.getString("status")).isEqualTo("error");
        assertThat(result.getString("error")).contains("is not on the classpath of this process")
                .contains("select a running integration that has it");
    }

    @Test
    void theLanguagesOfThisProcessAreEvaluatedAsBefore() {
        JsonObject result = ExpressionEvaluator.evaluate(new ToolContext(), "simple", "${body}", STOCK);

        assertThat(result.getString("status")).isEqualTo("ok");
        assertThat(result.getString("result")).isEqualTo(STOCK);
        assertThat(result.get("downloaded")).isNull();
    }

    @Test
    void aBrokenSimpleExpressionCarriesTheSyntaxErrorWithItsPosition() {
        JsonObject result = ExpressionEvaluator.evaluate(new ToolContext(), "simple", "${body", null);

        assertThat(result.getString("status")).isEqualTo("error");
        assertThat(result.getString("syntaxError")).isNotBlank();
    }

    @Test
    @EnabledIfSystemProperty(named = "camel.test.download", matches = "true")
    void jsonpathIsDownloadedAndEvaluated() {
        JsonObject result = ExpressionEvaluator.evaluate(new ToolContext(), "jsonpath",
                "$[?(@.sku == 'CAMEL-MUG')]", STOCK);

        assertThat(result.getString("downloaded")).contains("camel-jsonpath");
        assertThat(result.getString("status")).isEqualTo("ok");
        assertThat(result.getString("result")).contains("CAMEL-MUG");
    }
}
