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

/**
 * A configuration bean for a test execution.
 */
public class ITestConfig implements Serializable {

    private static final long serialVersionUID = -3641997669166217595L;

    private String moduleName;

    private String mavenGroup;

    private String mavenVersion;

    private String modulesPath;

    private Boolean includeTestDependencies;

    private Boolean unitTestEnabled;

    private String unitTestInclusionPattern;

    private String unitTestExclusionPattern;

    private String unitTestBasePackage;

    private Integer unitTestsExpectedNumber;

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

    public String getModulesPath() {
        return modulesPath;
    }

    public void setModulesPath(String modulesPath) {
        this.modulesPath = modulesPath;
    }

    public Boolean getIncludeTestDependencies() {
        return includeTestDependencies;
    }

    public void setIncludeTestDependencies(Boolean includeTestDependencies) {
        this.includeTestDependencies = includeTestDependencies;
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ITestConfig{");
        sb.append("moduleName='").append(moduleName).append('\'');
        sb.append(", mavenGroup='").append(mavenGroup).append('\'');
        sb.append(", mavenVersion='").append(mavenVersion).append('\'');
        sb.append(", modulesPath='").append(modulesPath).append('\'');
        sb.append(", includeTestDependencies=").append(includeTestDependencies);
        sb.append(", unitTestEnabled=").append(unitTestEnabled);
        sb.append(", unitTestInclusionPattern='").append(unitTestInclusionPattern).append('\'');
        sb.append(", unitTestExclusionPattern='").append(unitTestExclusionPattern).append('\'');
        sb.append(", unitTestBasePackage='").append(unitTestBasePackage).append('\'');
        sb.append(", unitTestsExpectedNumber=").append(unitTestsExpectedNumber);
        sb.append('}');
        return sb.toString();
    }
}
