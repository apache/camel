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

import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;

/**
 * Builder for the {@code ITestConfig} that enforces defaults values.
 */
public class ITestConfigBuilder {

    public static final String CONFIG_PREFIX = "itest.springboot.";

    private ITestConfig config;

    public ITestConfigBuilder() {
        this.config = new ITestConfig();
    }

    public ITestConfigBuilder(ITestConfig config) {
        this.config = config;
    }

    public ITestConfigBuilder module(String module) {
        config.setModuleName(module);
        return this;
    }

    public ITestConfigBuilder mavenGroup(String mavenGroup) {
        config.setMavenGroup(mavenGroup);
        return this;
    }

    public ITestConfigBuilder mavenVersion(String mavenVersion) {
        config.setMavenVersion(mavenVersion);
        return this;
    }

    public ITestConfigBuilder modulesPath(String path) {
        config.setModulesPath(path);
        return this;
    }

    public ITestConfigBuilder basePath(String basePath) {
        config.setModuleBasePath(basePath);
        return this;
    }

    public ITestConfigBuilder unitTestExpectedNumber(int number) {
        config.setUnitTestsExpectedNumber(number);
        return this;
    }

    public ITestConfigBuilder unitTestBasePackage(String pack) {
        config.setUnitTestBasePackage(pack);
        return this;
    }

    public ITestConfigBuilder unitTestInclusionPattern(String pattern) {
        config.setUnitTestInclusionPattern(pattern);
        return this;
    }

    public ITestConfigBuilder unitTestExclusionPattern(String pattern) {
        config.setUnitTestExclusionPattern(pattern);
        return this;
    }

    public ITestConfigBuilder autostart(Boolean autostart) {
        config.setAutoStartComponent(autostart);
        return this;
    }

    public ITestConfigBuilder resource(String file, String dest) {
        if (config.getResources() == null) {
            config.setResources(new HashMap<>());
        }
        config.getResources().put(file, dest);
        return this;
    }

    public ITestConfigBuilder dependency(String dependencyCanonicalForm) {
        if (config.getAdditionalDependencies() == null) {
            config.setAdditionalDependencies(new HashSet<>());
        }
        config.getAdditionalDependencies().add(dependencyCanonicalForm);
        return this;
    }

    public ITestConfigBuilder exclusion(String exclusionCanonicalForm) {
        if (exclusionCanonicalForm.split(":").length != 2) {
            throw new IllegalArgumentException("Expected exclusion in the form groupId:artifactId, got: " + exclusionCanonicalForm);
        }
        if (config.getMavenExclusions() == null) {
            config.setMavenExclusions(new HashSet<String>());
        }
        config.getMavenExclusions().add(exclusionCanonicalForm);
        return this;
    }

    public ITestConfigBuilder resource(String file) {
        return resource(file, file);
    }

    public ITestConfigBuilder disableJmx(String name) {
        if (config.getJmxDisabledNames() == null) {
            config.setJmxDisabledNames(new TreeSet<String>());
        }
        config.getJmxDisabledNames().add(name);
        return this;
    }

    public ITestConfigBuilder systemProperty(String name, String value) {
        if (config.getSystemProperties() == null) {
            config.setSystemProperties(new HashMap<String, String>());
        }
        config.getSystemProperties().put(name, value);
        return this;
    }

    public ITestConfigBuilder customLog(Boolean value) {
        config.setUseCustomLog(value);
        return this;
    }

    public ITestConfigBuilder ignoreLibraryMismatch(String libraryPrefix) {
        if (config.getIgnoreLibraryMismatch() == null) {
            config.setIgnoreLibraryMismatch(new HashSet<String>());
        }
        config.getIgnoreLibraryMismatch().add(libraryPrefix);
        return this;
    }

    public ITestConfigBuilder testLibraryVersion(String groupIdArtifactId, String version) {
        if (config.getTestLibraryVersions() == null) {
            config.setTestLibraryVersions(new HashMap<>());
        }
        config.getTestLibraryVersions().put(groupIdArtifactId, version);
        return this;
    }

    public ITestConfigBuilder includeTestDependencies(Boolean includeTestDependencies) {
        config.setIncludeTestDependencies(includeTestDependencies);
        return this;
    }

    public ITestConfigBuilder unitTestsEnabled(Boolean unitTestsEnabled) {
        config.setUnitTestEnabled(unitTestsEnabled);
        return this;
    }

    public ITestConfigBuilder springBootVersion(String springBootVersion) {
        config.setSpringBootVersion(springBootVersion);
        return this;
    }

    public ITestConfig build() {

        // Checking conditions
        if (config.getModuleName() == null) {
            fail("ModuleName is required");
        }

        // Set the defaults
        if (config.getUnitTestEnabled() == null) {
            config.setUnitTestEnabled(booleanPropertyOr("unitTestEnabled", true));
        }

        if (config.getMavenGroup() == null) {
            config.setMavenGroup(propertyOr("mavenGroup", "org.apache.camel"));
        }

        if (config.getMavenVersion() == null) {
            config.setMavenVersion(propertyOr("mavenVersion", null));
        }

        if (config.getMavenOfflineResolution() == null) {
            config.setMavenOfflineResolution(booleanPropertyOr("mavenOfflineResolution", false));
        }

        if (config.getUnitTestInclusionPattern() == null) {
            config.setUnitTestInclusionPattern(propertyOr("unitTestInclusionPattern", "^.*Test$")); // All tests
        }

        if (config.getUnitTestExclusionPattern() == null) {
            config.setUnitTestExclusionPattern(propertyOr("unitTestExclusionPattern", ".*(\\.integration\\..*|IntegrationTest$)")); // Integration test
        }

        if (config.getIncludeTestDependencies() == null) {
            config.setIncludeTestDependencies(booleanPropertyOr("includeTestDependencies", config.getUnitTestEnabled()));
        }

        if (config.getIncludeProvidedDependencies() == null) {
            config.setIncludeProvidedDependencies(booleanPropertyOr("includeProvidedDependencies", false));
        }

        if (config.getModulesPath() == null) {
            config.setModulesPath(propertyOr("modulesPath", "../../components/"));
        }

        if (config.getModuleBasePath() == null) {
            config.setModuleBasePath(config.getModulesPath() + config.getModuleName());
        }

        if (config.getUnitTestBasePackage() == null) {
            config.setUnitTestBasePackage(propertyOr("unitTestBasePackage", "org.apache.camel"));
        }

        if (config.getAutoStartComponent() == null) {
            config.setAutoStartComponent(booleanPropertyOr("autostartComponent", true));
        }

        if (config.getResources() == null) {
            config.setResources(new HashMap<>());
        }

        if (config.getAdditionalDependencies() == null) {
            config.setAdditionalDependencies(new HashSet<>());
        }

        if (config.getMavenExclusions() == null) {
            config.setMavenExclusions(new HashSet<>());
        }

        if (config.getJmxDisabledNames() == null) {
            config.setJmxDisabledNames(new HashSet<>());
        }

        if (config.getSystemProperties() == null) {
            config.setSystemProperties(new HashMap<>());
        }

        if (config.getUseCustomLog() == null) {
            config.setUseCustomLog(booleanPropertyOr("useCustomLog", true));
        }

        if (config.getIgnoreLibraryMismatch() == null) {
            config.setIgnoreLibraryMismatch(new HashSet<>());
        }

        if (config.getTestLibraryVersions() == null) {
            config.setTestLibraryVersions(new HashMap<>());
        }

        if (config.getSpringBootVersion() == null) {
            config.setSpringBootVersion(propertyOr("springBootVersion", null));
        }

        return config;
    }

    private void fail(String msg) {
        throw new IllegalStateException("Configuration is not complete: " + msg);
    }

    private String propertyOr(String name, String defaultVal) {
        String res = System.getProperty(CONFIG_PREFIX + name);
        if (res == null) {
            res = defaultVal;
        }
        return res;
    }

    private Boolean booleanPropertyOr(String name, Boolean defaultVal) {
        String prop = propertyOr(name, null);
        Boolean res = defaultVal;
        if (prop != null) {
            res = Boolean.valueOf(prop);
        }

        return res;
    }

    private Integer integerPropertyOr(String name, Integer defaultVal) {
        String prop = propertyOr(name, null);
        Integer res = defaultVal;
        if (prop != null) {
            res = Integer.valueOf(prop);
        }

        return res;
    }
}
