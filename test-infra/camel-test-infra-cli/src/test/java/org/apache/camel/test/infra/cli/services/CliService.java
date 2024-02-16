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
package org.apache.camel.test.infra.cli.services;

import java.util.stream.Stream;

import org.apache.camel.test.infra.common.services.TestService;
import org.apache.camel.test.infra.common.services.TestServiceUtil;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Test infra service for Camel Cli (Camel JBang)
 */
public interface CliService extends BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback, TestService {

    @Override
    default void beforeAll(ExtensionContext extensionContext) throws Exception {
        TestServiceUtil.tryInitialize(this, extensionContext);
    }

    @Override
    default void afterAll(ExtensionContext extensionContext) throws Exception {
        TestServiceUtil.tryShutdown(this, extensionContext);
    }

    @Override
    default void afterEach(ExtensionContext extensionContext) throws Exception {
        TestServiceUtil.tryShutdown(this, extensionContext);
    }

    @Override
    default void beforeEach(ExtensionContext extensionContext) throws Exception {
        TestServiceUtil.tryInitialize(this, extensionContext);
    }

    /**
     * Execute command in the camel jbang container
     *
     * @param  command String, command parameter for binary (ex: to execute `camel version`, just provide `version`
     * @return         String, the command output
     */
    String execute(String command);

    String executeBackground(String command);

    String executeGenericCommand(String command);

    /**
     * Copy a file inside the container
     *
     * @param source      String, the source file into the container
     * @param destination String, the destination file into the container
     */
    void copyFileInternally(String source, String destination);

    String getMountPoint();

    String getContainerLogs();

    int getDevConsolePort();

    Stream<String> listDirectory(String directoryPath);

    String id();

    String version();

    int getSshPort();

    String getSshPassword();
}
