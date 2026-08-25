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
package org.apache.camel.language.python3;

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
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisabledIfSystemProperty(named = "os.arch", matches = "(?i)(s390x|ppc64le)")
class Python3ResourceTest {

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
        return context.resolveLanguage("python3");
    }

    static Exchange exchangeWithBody(Object body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);
        return exchange;
    }

    @Test
    void classpathResourceUsesPython3Syntax() {
        assertThat(language().createExpression("resource:classpath:mypython3.py")
                .evaluate(exchangeWithBody(3), String.class)).isEqualTo("The result is 6");
    }

    @Test
    void fileResourceUsesPython3Syntax() throws Exception {
        Path script = tempDir.resolve("resource.py");
        Files.writeString(script, "f'file:{body}'");
        assertThat(language().createExpression("resource:file:" + script.toAbsolutePath())
                .evaluate(exchangeWithBody("Ada"), String.class)).isEqualTo("file:Ada");
    }

    @Test
    void missingClasspathResourceIsSyntaxException() {
        assertThatThrownBy(() -> language().createExpression("resource:classpath:missing-python3.py"))
                .isInstanceOf(ExpressionIllegalSyntaxException.class);
    }

    @Test
    void scriptingLanguageEvaluatesClasspathAndFileResources() throws Exception {
        ScriptingLanguage sl = (ScriptingLanguage) language();
        assertThat(sl.evaluate("resource:classpath:mypython3.py", Map.of("body", 4), String.class))
                .isEqualTo("The result is 8");

        Path script = tempDir.resolve("scripting.py");
        Files.writeString(script, "body + 1");
        assertThat(sl.evaluate("resource:file:" + script.toAbsolutePath(), Map.of("body", 9), Integer.class)).isEqualTo(10);
    }

    @Test
    void scriptingLanguageMissingResourceIsSyntaxException() {
        ScriptingLanguage sl = (ScriptingLanguage) language();
        assertThatThrownBy(() -> sl.evaluate("resource:classpath:missing-python3.py", Map.of(), Object.class))
                .isInstanceOf(ExpressionIllegalSyntaxException.class);
    }
}
