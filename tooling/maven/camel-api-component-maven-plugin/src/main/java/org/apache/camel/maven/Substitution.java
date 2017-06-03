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
 * Argument name substitution for {@link FileApiMethodGeneratorMojo}.
 */
public class Substitution {

    public String method;
    private String argName;
    private String argType;
    private String replacement;
    private boolean replaceWithType;

    public Substitution() {
    }

    public Substitution(String method, String argName, String argType, String replacement, boolean replaceWithType) {
        this.method = method;
        this.argName = argName;
        this.argType = argType;
        this.replacement = replacement;
        this.replaceWithType = replaceWithType;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getArgName() {
        return argName;
    }

    public void setArgName(String argName) {
        this.argName = argName;
    }

    public String getArgType() {
        return argType;
    }

    public void setArgType(String argType) {
        this.argType = argType;
    }

    public String getReplacement() {
        return replacement;
    }

    public void setReplacement(String replacement) {
        this.replacement = replacement;
    }

    public boolean isReplaceWithType() {
        return replaceWithType;
    }

    public void setReplaceWithType(boolean replaceWithType) {
        this.replaceWithType = replaceWithType;
    }
}
