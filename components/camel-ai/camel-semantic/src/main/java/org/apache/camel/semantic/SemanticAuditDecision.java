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
package org.apache.camel.semantic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/** Bean/processor for an explicit route action. Configure through normal Java, YAML or XML beans. */
public final class SemanticAuditDecision implements Processor {
    private Map<String, String> fields = new LinkedHashMap<>();
    private String evidenceProperty = SemanticAudit.REFERENCES;

    public Map<String, String> getFields() {
        return fields;
    }

    public void setFields(Map<String, String> fields) {
        this.fields = new LinkedHashMap<>(fields);
    }

    public String getEvidenceProperty() {
        return evidenceProperty;
    }

    public void setEvidenceProperty(String evidenceProperty) {
        this.evidenceProperty = evidenceProperty;
    }

    @Override
    public void process(Exchange exchange) {
        Object supplied = evidenceProperty == null ? null : exchange.getProperty(evidenceProperty);
        if (supplied != null
                && (!(supplied instanceof List<?> refs) || refs.stream().anyMatch(ref -> !(ref instanceof String)))) {
            throw new IllegalArgumentException("Audit evidence property must contain a list of event IDs");
        }
        @SuppressWarnings("unchecked")
        List<String> evidence = supplied == null ? List.of() : (List<String>) supplied;
        SemanticAudit.get(exchange.getContext()).decision(exchange, new LinkedHashMap<>(fields), evidence);
    }
}
