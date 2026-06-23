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
package org.apache.camel.telemetry.decorators;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.telemetry.Span;

public class AwsConfigSpanDecorator extends AbstractSpanDecorator {

    static final String CONFIG_OPERATION = "operation";
    static final String CONFIG_RULE_NAME = "ruleName";
    static final String CONFIG_RULE_SOURCE_IDENTIFIER = "ruleSourceIdentifier";
    static final String CONFIG_CONFORMANCE_PACK_NAME = "conformancePackName";

    /**
     * Constants copied from {@link org.apache.camel.component.aws.config.AWSConfigConstants}
     */
    static final String OPERATION = "CamelAwsConfigOperation";
    static final String RULE_NAME = "CamelAwsConfigRuleName";
    static final String RULE_SOURCE_IDENTIFIER = "CamelAwsConfigRuleSourceIdentifier";
    static final String CONFORMANCE_PACK_NAME = "CamelAwsConformancePackName";

    @Override
    public String getComponent() {
        return "aws-config";
    }

    @Override
    public String getComponentClassName() {
        return "org.apache.camel.component.aws.config.AWSConfigComponent";
    }

    @Override
    public void beforeTracingEvent(Span span, Exchange exchange, Endpoint endpoint) {
        super.beforeTracingEvent(span, exchange, endpoint);

        String operation = exchange.getIn().getHeader(OPERATION, String.class);
        if (operation != null) {
            span.setTag(CONFIG_OPERATION, operation);
        }

        String ruleName = exchange.getIn().getHeader(RULE_NAME, String.class);
        if (ruleName != null) {
            span.setTag(CONFIG_RULE_NAME, ruleName);
        }

        String ruleSourceIdentifier = exchange.getIn().getHeader(RULE_SOURCE_IDENTIFIER, String.class);
        if (ruleSourceIdentifier != null) {
            span.setTag(CONFIG_RULE_SOURCE_IDENTIFIER, ruleSourceIdentifier);
        }

        String conformancePackName = exchange.getIn().getHeader(CONFORMANCE_PACK_NAME, String.class);
        if (conformancePackName != null) {
            span.setTag(CONFIG_CONFORMANCE_PACK_NAME, conformancePackName);
        }
    }

}
