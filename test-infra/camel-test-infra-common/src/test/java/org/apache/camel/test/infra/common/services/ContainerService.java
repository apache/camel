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

package org.apache.camel.test.infra.common.services;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

public interface ContainerService<T extends GenericContainer> extends ExecutionCondition {

    @Override
    default ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext extensionContext) {

        if (ContainerEnvironmentUtil.isDockerAvailable()) {
            return ConditionEvaluationResult.enabled("Docker is available");
        }

        Logger logger = LoggerFactory.getLogger(ContainerService.class);
        logger.warn("Test {} is disabled because docker is not available", extensionContext.getElement().orElse(null));

        System.err.println(
                "Container-based tests were disabled because Docker is NOT available. Check the log files on target/failsafe-reports");
        return ConditionEvaluationResult.disabled("Docker is NOT available");
    }

    T getContainer();
}
