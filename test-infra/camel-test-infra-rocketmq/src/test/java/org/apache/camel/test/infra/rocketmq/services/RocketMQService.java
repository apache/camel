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
package org.apache.camel.test.infra.rocketmq.services;

import java.io.IOException;

import org.apache.camel.test.infra.common.services.TestService;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface RocketMQService extends TestService, BeforeTestExecutionCallback, AfterTestExecutionCallback {
    String nameserverAddress();

    default String defaultCluster() {
        return "DefaultCluster";
    }

    void createTopic(String topic);

    void deleteTopic(String topic) throws IOException, InterruptedException;

    @Override
    default void beforeAll(ExtensionContext extensionContext) {
        try {
            initialize();
        } catch (Exception e) {
            Logger log = LoggerFactory.getLogger(RocketMQService.class);

            final Object o = extensionContext.getTestInstance().get();
            log.error("Failed to initialize service {} for test {} on ({})", this.getClass().getSimpleName(),
                    extensionContext.getDisplayName(), o.getClass().getName());

            throw e;
        }
    }

    @Override
    default void beforeTestExecution(ExtensionContext extensionContext) {
        //no op
    }

    @Override
    default void afterAll(ExtensionContext extensionContext) {
        shutdown();
    }

    @Override
    default void afterTestExecution(ExtensionContext context) {
        //no op
    }
}
