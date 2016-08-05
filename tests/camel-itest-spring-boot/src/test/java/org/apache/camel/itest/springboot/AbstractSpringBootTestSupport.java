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
package org.apache.camel.itest.springboot;

import java.lang.reflect.Method;

import org.apache.camel.itest.springboot.arquillian.ArquillianSyncBootJarLauncher;
import org.apache.camel.itest.springboot.util.SpringBootContainerFacade;
import org.junit.Before;

/**
 * This is the base class of all spring-boot integration tests.
 */
public abstract class AbstractSpringBootTestSupport {

    protected ITestConfig config;

    private SpringBootContainerFacade facade;

    /**
     * Starts a spring-boot container with the module under test.
     *
     * @throws Exception
     */
    @Before
    public void startSpringBoot() throws Exception {
        this.config = retrieveConfig();

        ArquillianSyncBootJarLauncher launcher = new ArquillianSyncBootJarLauncher();
        launcher.run(new String[]{});

        this.facade = new SpringBootContainerFacade(launcher.getClassLoader());
    }

    protected static String inferModuleName(Class<?> testClass) {
        String name = testClass.getSimpleName();
        int id1 = name.indexOf("Test");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < id1; i++) {
            char c = name.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && sb.length() > 0) {
                sb.append("-");
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    protected String defaultComponentName() throws Exception {
        return config.getModuleName().replace("camel-", "");
    }

    protected void runComponentTest(ITestConfig config) throws Exception {
        this.runComponentTest(config, defaultComponentName());
    }

    protected void runComponentTest(ITestConfig config, String componentName) throws Exception {
        facade.executeTest("module", config, componentName);
    }

    protected void runDataformatTest(ITestConfig config) throws Exception {
        this.runDataformatTest(config, defaultComponentName());
    }

    protected void runDataformatTest(ITestConfig config, String dataFormatName) throws Exception {
        facade.executeTest("dataformat", config, dataFormatName);
    }

    protected void runLanguageTest(ITestConfig config) throws Exception {
        this.runLanguageTest(config, defaultComponentName());
    }

    protected void runLanguageTest(ITestConfig config, String language) throws Exception {
        facade.executeTest("language", config, language);
    }

    protected void runModuleUnitTestsIfEnabled(ITestConfig config) throws Exception {
        if (config.getUnitTestEnabled()) {
            this.runModuleUnitTests(config);
        }
    }

    protected void runModuleUnitTests(ITestConfig config) throws Exception {
        facade.executeTest("unittest", config, config.getModuleName().replace("camel-", ""));
    }

    protected ITestConfig retrieveConfig() throws Exception {
        Method confMethod = this.getClass().getMethod("createTestConfig");
        ITestConfig conf = (ITestConfig) confMethod.invoke(null);
        return conf;
    }

}
