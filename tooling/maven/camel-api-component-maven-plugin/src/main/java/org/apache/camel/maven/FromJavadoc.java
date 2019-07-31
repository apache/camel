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
package org.apache.camel.maven;

/**
 * Javadoc API generator properties.
 */
public class FromJavadoc {

    protected String excludePackages = JavadocApiMethodGeneratorMojo.DEFAULT_EXCLUDE_PACKAGES;

    protected String excludeClasses;

    protected String includeMethods;

    protected String excludeMethods;

    protected Boolean includeStaticMethods;

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

    public String getIncludeMethods() {
        return includeMethods;
    }

    public void setIncludeMethods(String includeMethods) {
        this.includeMethods = includeMethods;
    }

    public String getExcludeMethods() {
        return excludeMethods;
    }

    public void setExcludeMethods(String excludeMethods) {
        this.excludeMethods = excludeMethods;
    }

    public Boolean getIncludeStaticMethods() {
        return includeStaticMethods;
    }

    public void setIncludeStaticMethods(Boolean includeStaticMethods) {
        this.includeStaticMethods = includeStaticMethods;
    }
}
