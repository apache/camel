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

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/**
 * A configuration bean for a test execution.
 */
public class ITestConfig implements Serializable {

    private static final int DEFAULT_SPRING_BOOT_MAJOR_VERSION = 1;

    private static final long serialVersionUID = -3641997669166217595L;

    private String moduleName;

    private String mavenGroup;

    private String mavenVersion;

    private Boolean mavenOfflineResolution;

    private String modulesPath;

    private String moduleBasePath;

    private Boolean includeTestDependencies;

    private Boolean includeProvidedDependencies;

    private Boolean unitTestEnabled;

    private String unitTestInclusionPattern;

    private String unitTestExclusionPattern;

    private String unitTestBasePackage;

    private Integer unitTestsExpectedNumber;

    private Map<String, String> resources;

    private Set<String> additionalDependencies;

    private Set<String> mavenExclusions;

    private Boolean autoStartComponent;

    private Set<String> jmxDisabledNames;

    private Map<String, String> systemProperties;

    private Boolean useCustomLog;

    private Set<String> ignoreLibraryMismatch;

    private Map<String, String> testLibraryVersions;

    private String springBootVersion;

    public ITestConfig() {
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getMavenGroup() {
        return mavenGroup;
    }

    public void setMavenGroup(String mavenGroup) {
        this.mavenGroup = mavenGroup;
    }

    public String getMavenVersion() {
        return mavenVersion;
    }

    public void setMavenVersion(String mavenVersion) {
        this.mavenVersion = mavenVersion;
    }

    public Boolean getMavenOfflineResolution() {
        return mavenOfflineResolution;
    }

    public void setMavenOfflineResolution(Boolean mavenOfflineResolution) {
        this.mavenOfflineResolution = mavenOfflineResolution;
    }

    public String getModulesPath() {
        return modulesPath;
    }

    public void setModulesPath(String modulesPath) {
        this.modulesPath = modulesPath;
    }

    public String getModuleBasePath() {
        return moduleBasePath;
    }

    public void setModuleBasePath(String moduleBasePath) {
        this.moduleBasePath = moduleBasePath;
    }

    public Boolean getIncludeTestDependencies() {
        return includeTestDependencies;
    }

    public void setIncludeTestDependencies(Boolean includeTestDependencies) {
        this.includeTestDependencies = includeTestDependencies;
    }

    public Boolean getIncludeProvidedDependencies() {
        return includeProvidedDependencies;
    }

    public void setIncludeProvidedDependencies(Boolean includeProvidedDependencies) {
        this.includeProvidedDependencies = includeProvidedDependencies;
    }

    public Boolean getUnitTestEnabled() {
        return unitTestEnabled;
    }

    public void setUnitTestEnabled(Boolean unitTestEnabled) {
        this.unitTestEnabled = unitTestEnabled;
    }

    public String getUnitTestInclusionPattern() {
        return unitTestInclusionPattern;
    }

    public void setUnitTestInclusionPattern(String unitTestInclusionPattern) {
        this.unitTestInclusionPattern = unitTestInclusionPattern;
    }

    public String getUnitTestExclusionPattern() {
        return unitTestExclusionPattern;
    }

    public void setUnitTestExclusionPattern(String unitTestExclusionPattern) {
        this.unitTestExclusionPattern = unitTestExclusionPattern;
    }

    public String getUnitTestBasePackage() {
        return unitTestBasePackage;
    }

    public void setUnitTestBasePackage(String unitTestBasePackage) {
        this.unitTestBasePackage = unitTestBasePackage;
    }

    public Integer getUnitTestsExpectedNumber() {
        return unitTestsExpectedNumber;
    }

    public void setUnitTestsExpectedNumber(Integer unitTestsExpectedNumber) {
        this.unitTestsExpectedNumber = unitTestsExpectedNumber;
    }

    public Map<String, String> getResources() {
        return resources;
    }

    public Set<String> getAdditionalDependencies() {
        return additionalDependencies;
    }

    public void setAdditionalDependencies(Set<String> additionalDependencies) {
        this.additionalDependencies = additionalDependencies;
    }

    public Set<String> getMavenExclusions() {
        return mavenExclusions;
    }

    public void setMavenExclusions(Set<String> mavenExclusions) {
        this.mavenExclusions = mavenExclusions;
    }

    public void setResources(Map<String, String> resources) {
        this.resources = resources;
    }

    public Boolean getAutoStartComponent() {
        return autoStartComponent;
    }

    public void setAutoStartComponent(Boolean autoStartComponent) {
        this.autoStartComponent = autoStartComponent;
    }

    public Set<String> getJmxDisabledNames() {
        return jmxDisabledNames;
    }

    public void setJmxDisabledNames(Set<String> jmxDisabledNames) {
        this.jmxDisabledNames = jmxDisabledNames;
    }

    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }

    public void setSystemProperties(Map<String, String> systemProperties) {
        this.systemProperties = systemProperties;
    }

    public Boolean getUseCustomLog() {
        return useCustomLog;
    }

    public void setUseCustomLog(Boolean useCustomLog) {
        this.useCustomLog = useCustomLog;
    }

    public Set<String> getIgnoreLibraryMismatch() {
        return ignoreLibraryMismatch;
    }

    public void setIgnoreLibraryMismatch(Set<String> ignoreLibraryMismatch) {
        this.ignoreLibraryMismatch = ignoreLibraryMismatch;
    }

    public Map<String, String> getTestLibraryVersions() {
        return testLibraryVersions;
    }

    public void setTestLibraryVersions(Map<String, String> testLibraryVersions) {
        this.testLibraryVersions = testLibraryVersions;
    }

    public String getSpringBootVersion() {
        return springBootVersion;
    }

    public Integer getSpringBootMajorVersion() {
        if (springBootVersion != null) {
            return Integer.parseInt(springBootVersion.substring(0, 1));
        } else {
            return DEFAULT_SPRING_BOOT_MAJOR_VERSION;
        }
    }

    public void setSpringBootVersion(String springBootVersion) {
        this.springBootVersion = springBootVersion;
    }

    @Override
    public String toString() {
        return "ITestConfig{" +
                "moduleName='" + moduleName + '\'' +
                ", mavenGroup='" + mavenGroup + '\'' +
                ", mavenVersion='" + mavenVersion + '\'' +
                ", mavenOfflineResolution=" + mavenOfflineResolution +
                ", modulesPath='" + modulesPath + '\'' +
                ", moduleBasePath='" + moduleBasePath + '\'' +
                ", includeTestDependencies=" + includeTestDependencies +
                ", includeProvidedDependencies=" + includeProvidedDependencies +
                ", unitTestEnabled=" + unitTestEnabled +
                ", unitTestInclusionPattern='" + unitTestInclusionPattern + '\'' +
                ", unitTestExclusionPattern='" + unitTestExclusionPattern + '\'' +
                ", unitTestBasePackage='" + unitTestBasePackage + '\'' +
                ", unitTestsExpectedNumber=" + unitTestsExpectedNumber +
                ", resources=" + resources +
                ", additionalDependencies=" + additionalDependencies +
                ", mavenExclusions=" + mavenExclusions +
                ", autoStartComponent=" + autoStartComponent +
                ", jmxDisabledNames=" + jmxDisabledNames +
                ", systemProperties=" + systemProperties +
                ", useCustomLog=" + useCustomLog +
                ", ignoreLibraryMismatch=" + ignoreLibraryMismatch +
                ", testLibraryVersions=" + testLibraryVersions +
                ", springBootVersion=" + springBootVersion +
                '}';
    }
}
