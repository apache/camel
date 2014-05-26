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
package org.apache.camel.maven;

import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * Represents an API to use for generating Camel Component.
 */
public class ApiProxy {
    private String apiName;
    private String proxyClass;

    private Substitution[] substitutions = new Substitution[0];

    private String excludePackages = JavadocApiMethodGeneratorMojo.DEFAULT_EXCLUDE_PACKAGES;
    private String excludeClasses;

    private File signatureFile;

    private Map<String, String> aliases = Collections.EMPTY_MAP;

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getProxyClass() {
        return proxyClass;
    }

    public void setProxyClass(String proxyClass) {
        this.proxyClass = proxyClass;
    }

    public Substitution[] getSubstitutions() {
        return substitutions;
    }

    public void setSubstitutions(Substitution[] substitutions) {
        this.substitutions = substitutions;
    }

    public String getExcludePackages() {
        return excludePackages;
    }

    public void setExcludePackages(String excludePackages) {
        this.excludePackages = excludePackages;
    }

    public String getExcludeClasses() {
        return excludeClasses;
    }

    public void setExcludeClasses(String excludeClasses) {
        this.excludeClasses = excludeClasses;
    }

    public File getSignatureFile() {
        return signatureFile;
    }

    public void setSignatureFile(File signatureFile) {
        this.signatureFile = signatureFile;
    }

    public Map<String, String> getAliases() {
        return aliases;
    }

    public void setAliases(Map<String, String> aliases) {
        this.aliases = aliases;
    }

    public void validate() {
        if (apiName == null || proxyClass == null) {
            throw new IllegalArgumentException("Properties apiName and proxyClass are required");
        }
    }
}
