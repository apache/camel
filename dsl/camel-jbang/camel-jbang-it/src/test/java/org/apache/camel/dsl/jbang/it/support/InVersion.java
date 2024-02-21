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

import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.apache.commons.lang3.StringUtils;

import static org.apache.camel.util.StringHelper.sanitize;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ExtendWith(InVersion.InVersionCondition.class)
public @interface InVersion {

    Logger LOGGER = LoggerFactory.getLogger(InVersion.class);

    String from() default "";

    String to() default "";

    class InVersionCondition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            final JBangTestSupport currTestClass = (JBangTestSupport) context.getTestInstance().get();
            if (currTestClass == null) {
                return ConditionEvaluationResult.enabled("unable to verify version");
            }
            return context.getTestMethod()
                    .filter(method -> method.isAnnotationPresent(InVersion.class))
                    .map(method -> method.getAnnotation(InVersion.class))
                    .map(annotation -> {
                        if (StringUtils.isNotBlank(annotation.from()) && StringUtils.isNotBlank(annotation.to())) {
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("from={},to={},{}", sanitize(annotation.from()), annotation.to(),
                                        VersionHelper.isBetween(currTestClass.version(), annotation.from(), annotation.to()));
                            }
                            return VersionHelper.isBetween(currTestClass.version(), annotation.from(), annotation.to());
                        } else if (StringUtils.isNotBlank(annotation.from())) {
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("from={},{}", annotation.from(),
                                        VersionHelper.isGE(currTestClass.version(), annotation.from()));
                            }
                            return VersionHelper.isGE(currTestClass.version(), annotation.from());
                        }
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("to={},{}", annotation.to(),
                                    VersionHelper.isLE(currTestClass.version(), annotation.to()));
                        }
                        return VersionHelper.isLE(currTestClass.version(), annotation.to());
                    })
                    .orElse(Boolean.TRUE)
                            ? ConditionEvaluationResult.enabled("the version is in range")
                            : ConditionEvaluationResult.disabled("the version is not in range");
        }
    }
}
