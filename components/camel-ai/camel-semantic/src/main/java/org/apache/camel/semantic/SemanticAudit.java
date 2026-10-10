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

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.semantic.internal.SemanticAuditService;

/** Route-facing entry point for explicit application decisions. Evaluation lifecycle plumbing is internal. */
public final class SemanticAudit {
    /** Evidence IDs from the latest semantic expression; save them if a later decision needs earlier evaluations. */
    public static final String REFERENCES = "CamelSemanticAuditReferences";
    private final CamelContext context;

    private SemanticAudit(CamelContext context) {
        this.context = context;
    }

    public static SemanticAudit get(CamelContext context) {
        return new SemanticAudit(context);
    }

    /** Record the action actually declared by the route, independently of any expert's result meaning. */
    public void decision(Exchange exchange, Map<String, String> decision, List<String> evidence) {
        SemanticAuditService.get(context).decision(exchange, decision, evidence);
    }
}
