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

/**
 * Represents method alias
 */
public class ApiMethodAlias {

    private String methodPattern;
    private String methodAlias;

    public ApiMethodAlias() {
    }

    public ApiMethodAlias(String methodPattern, String methodAlias) {
        this.methodPattern = methodPattern;
        this.methodAlias = methodAlias;
    }

    public String getMethodPattern() {
        return methodPattern;
    }

    public void setMethodPattern(String methodPattern) {
        this.methodPattern = methodPattern;
    }

    public String getMethodAlias() {
        return methodAlias;
    }

    public void setMethodAlias(String methodAlias) {
        this.methodAlias = methodAlias;
    }
}
