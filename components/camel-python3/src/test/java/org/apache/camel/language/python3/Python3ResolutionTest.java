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

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Language;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledIfSystemProperty(named = "os.arch", matches = "(?i)(s390x|ppc64le)")
class Python3ResolutionTest {

    @Test
    void resolveLanguageUsesSpiWhenRegistryHasNoOverride() {
        CamelContext context = new DefaultCamelContext();
        context.start();
        try {
            Language language = context.resolveLanguage("python3");
            assertThat(language).isInstanceOf(Python3Language.class);
            assertThat(context.resolveLanguage("python3")).isSameAs(language);
        } finally {
            context.stop();
        }
    }

    @Test
    void registryBindingOverridesSpiBeforeFirstResolve() {
        Python3Language custom = Python3Language.createWithHostAccess();
        CamelContext context = new DefaultCamelContext();
        context.getRegistry().bind("python3", custom);
        context.start();
        try {
            Language resolved = context.resolveLanguage("python3");
            assertThat(resolved).isSameAs(custom);

            DefaultExchange exchange = new DefaultExchange(context);
            assertThat(resolved.createExpression("exchange.getExchangeId()").evaluate(exchange, String.class))
                    .isEqualTo(exchange.getExchangeId());
        } finally {
            context.stop();
        }
    }
}
