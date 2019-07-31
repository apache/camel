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
package org.apache.camel.component.salesforce.api.dto;

public class SObjectUrls extends AbstractDTOBase {

    private String sobject;
    private String describe;
    private String rowTemplate;
    private String passwordUtilities;
    private String approvalLayouts;
    private String quickActions;
    private String caseArticleSuggestions;
    private String listviews;
    private String layouts;
    private String namedLayouts;
    private String compactLayouts;
    private String caseRowArticleSuggestions;
    private String push;
    private String defaultValues;

    public String getSobject() {
        return sobject;
    }

    public void setSobject(String sobject) {
        this.sobject = sobject;
    }

    public String getDescribe() {
        return describe;
    }

    public void setDescribe(String describe) {
        this.describe = describe;
    }

    public String getRowTemplate() {
        return rowTemplate;
    }

    public void setRowTemplate(String rowTemplate) {
        this.rowTemplate = rowTemplate;
    }

    public String getPasswordUtilities() {
        return passwordUtilities;
    }

    public void setPasswordUtilities(String passwordUtilities) {
        this.passwordUtilities = passwordUtilities;
    }

    public String getApprovalLayouts() {
        return approvalLayouts;
    }

    public void setApprovalLayouts(String approvalLayouts) {
        this.approvalLayouts = approvalLayouts;
    }

    public String getQuickActions() {
        return quickActions;
    }

    public void setQuickActions(String quickActions) {
        this.quickActions = quickActions;
    }

    public String getCaseArticleSuggestions() {
        return caseArticleSuggestions;
    }

    public void setCaseArticleSuggestions(String caseArticleSuggestions) {
        this.caseArticleSuggestions = caseArticleSuggestions;
    }

    public String getListviews() {
        return listviews;
    }

    public void setListviews(String listviews) {
        this.listviews = listviews;
    }

    public String getLayouts() {
        return layouts;
    }

    public void setLayouts(String layouts) {
        this.layouts = layouts;
    }

    public String getNamedLayouts() {
        return namedLayouts;
    }

    public void setNamedLayouts(String namedLayouts) {
        this.namedLayouts = namedLayouts;
    }

    public String getCompactLayouts() {
        return compactLayouts;
    }

    public void setCompactLayouts(String compactLayouts) {
        this.compactLayouts = compactLayouts;
    }

    public String getCaseRowArticleSuggestions() {
        return caseRowArticleSuggestions;
    }

    public void setCaseRowArticleSuggestions(String caseRowArticleSuggestions) {
        this.caseRowArticleSuggestions = caseRowArticleSuggestions;
    }

    public String getPush() {
        return push;
    }

    public void setPush(String push) {
        this.push = push;
    }

    public String getDefaultValues() {
        return defaultValues;
    }

    public void setDefaultValues(String defaultValues) {
        this.defaultValues = defaultValues;
    }
}
