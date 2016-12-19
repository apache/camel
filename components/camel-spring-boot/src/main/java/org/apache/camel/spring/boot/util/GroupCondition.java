/**
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
package org.apache.camel.spring.boot.util;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.bind.RelaxedPropertyResolver;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class GroupCondition extends SpringBootCondition {
    private final String group;
    private final String single;
    private final boolean groupDefault;
    private final boolean singleDefault;

    public GroupCondition(String group, String single) {
        this(group, true, single, true);
    }

    public GroupCondition(String group, boolean groupDefault, String single, boolean singleDefault) {
        this.group = group.endsWith(".") ? group : group + ".";
        this.groupDefault = groupDefault;

        this.single = group.endsWith(".") ? single : single + ".";
        this.singleDefault = singleDefault;
    }

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext conditionContext, AnnotatedTypeMetadata annotatedTypeMetadata) {
        boolean groupEnabled = isEnabled(conditionContext, this.group, true);
        ConditionMessage.Builder message = ConditionMessage.forCondition(this.single);

        if (isEnabled(conditionContext, this.single, groupEnabled)) {
            return ConditionOutcome.match(message.because("enabled"));
        }

        return ConditionOutcome.noMatch(message.because("not enabled"));
    }

    public static boolean isEnabled(ConditionContext context, String prefix, boolean defaultValue) {
        RelaxedPropertyResolver resolver = new RelaxedPropertyResolver(context.getEnvironment(), prefix);
        return resolver.getProperty("enabled", Boolean.class, defaultValue);
    }
}
