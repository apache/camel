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
package org.apache.camel.dsl.jbang.it.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ExtendWith(JiraIssue.JiraIssueCondition.class)
public @interface JiraIssue {
    String value();

    class JiraIssueCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (context.getTestInstance().isEmpty()) {
                return ConditionEvaluationResult.enabled("unable to verify version");
            }
            final JBangTestSupport currTestClass = (JBangTestSupport) context.getTestInstance().get();
            if (currTestClass == null) {
                return ConditionEvaluationResult.enabled("unable to verify version");
            }
            return context.getTestMethod()
                    .filter(method -> method.isAnnotationPresent(JiraIssue.class))
                    .map(method -> method.getAnnotation(JiraIssue.class).value())
                    .map(issue -> JiraUtil.isIssueSolved(issue, currTestClass.version()))
                    .orElse(Boolean.TRUE)
                            ? ConditionEvaluationResult.enabled("Jira Issue is solved in this version")
                            : ConditionEvaluationResult.disabled("Jira Issue is not solved yet in this version");
        }
    }
}
