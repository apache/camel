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
package org.apache.camel.language.quickjs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuickjsResourceTest {

    static CamelContext context;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void startContext() {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterAll
    static void stopContext() {
        context.stop();
    }

    static Language language() {
        return context.resolveLanguage("quickjs");
    }

    static Exchange exchangeWithBody(Object body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);
        return exchange;
    }

    @Test
    void classpathResource() {
        assertThat(language().createExpression("resource:classpath:myquickjs.js")
                .evaluate(exchangeWithBody(3), String.class)).isEqualTo("The result is 6");
    }

    @Test
    void fileResource() throws Exception {
        Path script = tempDir.resolve("resource.js");
        Files.writeString(script, "'file:' + body");
        assertThat(language().createExpression("resource:file:" + script.toAbsolutePath())
                .evaluate(exchangeWithBody("Ada"), String.class)).isEqualTo("file:Ada");
    }

    @Test
    void missingClasspathResourceIsSyntaxException() {
        assertThatThrownBy(() -> language().createExpression("resource:classpath:missing-quickjs.js"))
                .isInstanceOf(ExpressionIllegalSyntaxException.class);
    }

    @Test
    void scriptingLanguageEvaluatesClasspathResource() {
        ScriptingLanguage sl = (ScriptingLanguage) language();
        assertThat(sl.evaluate("resource:classpath:myquickjs.js", Map.of("body", 4), String.class))
                .isEqualTo("The result is 8");
    }
}
