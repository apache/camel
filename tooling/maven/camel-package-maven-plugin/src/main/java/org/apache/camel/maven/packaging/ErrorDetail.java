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
package org.apache.camel.maven.packaging;

import java.util.ArrayList;
import java.util.List;

/**
 * Holder to keep information about detected errors.
 */
public final class ErrorDetail {

    private String kind;

    private boolean missingLabel;
    private boolean missingDescription;
    private boolean missingSyntax;
    private boolean missingUriPath;
    private final List<String> missingComponentDocumentation = new ArrayList<>();
    private final List<String> missingEndpointDocumentation = new ArrayList<>();

    public boolean hasErrors() {
        return missingLabel || missingDescription || missingSyntax || missingUriPath || !missingComponentDocumentation.isEmpty() || !missingEndpointDocumentation.isEmpty();
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public boolean isMissingUriPath() {
        return missingUriPath;
    }

    public void setMissingUriPath(boolean missingUriPath) {
        this.missingUriPath = missingUriPath;
    }

    public boolean isMissingLabel() {
        return missingLabel;
    }

    public void setMissingLabel(boolean missingLabel) {
        this.missingLabel = missingLabel;
    }

    public boolean isMissingDescription() {
        return missingDescription;
    }

    public void setMissingDescription(boolean missingDescription) {
        this.missingDescription = missingDescription;
    }

    public boolean isMissingSyntax() {
        return missingSyntax;
    }

    public void setMissingSyntax(boolean missingSyntax) {
        this.missingSyntax = missingSyntax;
    }

    public void addMissingComponentDoc(String option) {
        missingComponentDocumentation.add(option);
    }

    public void addMissingEndpointDoc(String option) {
        missingEndpointDocumentation.add(option);
    }

    public List<String> getMissingComponentDocumentation() {
        return missingComponentDocumentation;
    }

    public List<String> getMissingEndpointDocumentation() {
        return missingEndpointDocumentation;
    }

}
