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
package org.apache.camel.reifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.SwitchCaseDefinition;
import org.apache.camel.model.SwitchDefinition;
import org.apache.camel.model.SwitchValueDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.processor.SwitchProcessor;
import org.apache.camel.spi.NodeIdFactory;

public class SwitchReifier extends ProcessorReifier<SwitchDefinition> {
    public SwitchReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (SwitchDefinition) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        if (definition.getSelector() == null || definition.getSelector().getExpressionType() == null) {
            throw new IllegalArgumentException("Switch selector requires an expression");
        }
        List<String> keys = definition.getKeys();
        if (keys.stream().anyMatch(k -> k == null || k.isBlank()) || new HashSet<>(keys).size() != keys.size()) {
            throw new IllegalArgumentException("Switch keys must be nonblank and unique");
        }
        // Validate the complete table before creating endpoint processors.
        List<Object> caseKeys = new ArrayList<>();
        List<String> uris = new ArrayList<>();
        for (SwitchCaseDefinition c : definition.getCases()) {
            Object key;
            if (keys.isEmpty()) {
                if (c.getValue() == null || !c.getValues().isEmpty()) {
                    throw new IllegalArgumentException("Scalar switch cases require value and cannot declare values");
                }
                key = c.getValue().toLowerCase(Locale.ENGLISH);
            } else {
                if (c.getValue() != null) {
                    throw new IllegalArgumentException("Composite switch cases cannot declare scalar value");
                }
                Map<String, Object> values = new LinkedHashMap<>();
                for (SwitchValueDefinition literal : c.getValues()) {
                    if (literal.getName() == null || values.containsKey(literal.getName())) {
                        throw new IllegalArgumentException("Duplicate or missing switch value name: " + literal.getName());
                    }
                    values.put(literal.getName(), literal.asLiteral());
                }
                if (!values.keySet().equals(new HashSet<>(keys))) {
                    throw new IllegalArgumentException("Switch case values must contain exactly the declared keys: " + keys);
                }
                key = SwitchProcessor.compositeKey(values, keys);
            }
            caseKeys.add(key);
            uris.add(staticUri(c.getUri()));
        }
        if (new HashSet<>(caseKeys).size() != caseKeys.size()) {
            throw new IllegalArgumentException("Duplicate switch case after literal normalization");
        }
        String otherwiseUri = definition.getOtherwise() == null ? null : staticUri(definition.getOtherwise());
        Expression selector = createExpression(definition.getSelector().getExpressionType());
        NodeIdFactory ids = camelContext.getCamelContextExtension().getContextPlugin(NodeIdFactory.class);
        Map<Object, Processor> cases = new LinkedHashMap<>();
        for (int i = 0; i < definition.getCases().size(); i++) {
            SwitchCaseDefinition c = definition.getCases().get(i);
            c.setParent(definition);
            c.setCamelContext(camelContext);
            c.idOrCreate(ids);
            ToDefinition send = c.getToDefinition();
            send.setUri(uris.get(i));
            cases.put(caseKeys.get(i), createSend(send));
        }
        Processor otherwise = null;
        if (otherwiseUri != null) {
            definition.idOrCreate(ids);
            ToDefinition send = definition.getOtherwiseDefinition();
            send.setUri(otherwiseUri);
            otherwise = createSend(send);
        }
        SwitchProcessor answer = new SwitchProcessor(camelContext, selector, keys, cases, otherwise);
        answer.setDisabled(isDisabled(camelContext, definition));
        return answer;
    }

    private Processor createSend(ToDefinition send) throws Exception {
        send.setParent(definition);
        send.setCamelContext(camelContext);
        return createOutputsProcessor(List.of(send));
    }

    private String staticUri(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("Switch case requires a nonblank uri");
        }
        String resolved = parseString(uri);
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalArgumentException("Switch URI must resolve to a nonblank destination");
        }
        if (resolved.contains("${") || resolved.contains("$simple{")) {
            throw new IllegalArgumentException("Switch URI must be static; Simple expressions are not supported");
        }
        return resolved;
    }
}
