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

package org.apache.camel.test.infra.ftp.services.embedded;

import org.apache.camel.test.infra.ftp.services.FtpTestService;
import org.junit.jupiter.api.extension.ExtensionContext;

public class SftpEmbeddedTestService extends SftpEmbeddedService implements FtpTestService {
    private ExtensionContext context;

    public SftpEmbeddedTestService() {
        super(false);
    }

    public SftpEmbeddedTestService(boolean rootDirMode) {
        super(rootDirMode);
    }

    protected SftpEmbeddedTestService(boolean rootDirMode, EmbeddedConfiguration embeddedConfiguration) {
        super(rootDirMode, embeddedConfiguration);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    public void tearDown() {
        super.tearDown();
    }

    @Override
    public void registerProperties() {
        ExtensionContext.Store store = context.getStore(ExtensionContext.Namespace.GLOBAL);
        registerProperties(store::put);
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        this.context = extensionContext;
        this.initExtensionContext(extensionContext);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        this.context = null;
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        shutdown();
        this.context = null;
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        this.context = extensionContext;
        this.initExtensionContext(extensionContext);
        initialize();
    }

    private void initExtensionContext(ExtensionContext extensionContext) {
        this.context = extensionContext;
        this.testDirectory = context.getRequiredTestClass().getSimpleName();
    }
}
