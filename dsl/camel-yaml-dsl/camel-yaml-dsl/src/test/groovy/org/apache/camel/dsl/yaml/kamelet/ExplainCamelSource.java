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

package org.apache.camel.dsl.yaml.kamelet;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.KameletRouteTemplate;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.spi.KameletProperty;
import org.apache.camel.spi.KameletSpec;

@KameletSpec(name = "explain-camel-source",
        title = "Explain Camel Source",
        description = "Explains Apache Camel in multiple languages.",
        properties = {
            @KameletProperty(
                    required = true,
                    name = "language",
                    title = "Language",
                    defaultValue = "en",
                    enumeration = { "en", "de", "it", "es", "fr" },
                    description = "The language to use.")
        })
public class ExplainCamelSource extends KameletRouteTemplate {

    private final Map<String, String> explanations = new HashMap<>();

    public ExplainCamelSource() {
        explanations.put("en",
                "Camel is an Open Source integration framework that empowers you to quickly and easily integrate various systems.");
        explanations.put("de",
                "Camel ist ein Open Source Integration Framework, welches Ihnen ermoeglicht, schnell und einfach verschiedene Systeme zu integrieren.");
        explanations.put("it",
                "Camel è un quadro di integrazione Open Source che permette di integrare rapidamente e facilmente vari sistemi.");
        explanations.put("es",
                "Camel es un marco de integración Open Source que le permite integrar rápidamente y fácilmente diversos sistemas.");
        explanations.put("fr",
                "Camel est un cadre d'intégration Open Source qui vous permet d'intégrer rapidement et facilement différents systèmes.");
    }

    @Override
    public void template(RouteTemplateDefinition template) {
        template.from("timer:once?repeatCount=1")
                .setProperty("language", constant("{{language}}"))
                .process(this::explain)
                .to(kameletSink());
    }

    private void explain(Exchange exchange) {
        String languageKey = exchange.getProperty("language", String.class);
        exchange.getMessage().setBody(explanations.getOrDefault(languageKey,
                "Sorry, I do not speak the language '%s'".formatted(languageKey)));
    }
}
