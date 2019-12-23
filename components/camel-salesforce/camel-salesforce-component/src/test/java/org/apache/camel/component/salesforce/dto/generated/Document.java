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
package org.apache.camel.component.salesforce.dto.generated;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import org.apache.camel.component.salesforce.api.dto.AbstractDescribedSObjectBase;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.dto.SObjectDescriptionUrls;
import org.apache.camel.component.salesforce.api.dto.SObjectField;

//CHECKSTYLE:OFF
/**
 * Salesforce DTO for SObject Document
 */
@XStreamAlias("Document")
public class Document extends AbstractDescribedSObjectBase {

    public Document() {
        getAttributes().setType("Document");
    }

    private static final SObjectDescription DESCRIPTION = createSObjectDescription();

    private String FolderId;

    @JsonProperty("FolderId")
    public String getFolderId() {
        return this.FolderId;
    }

    @JsonProperty("FolderId")
    public void setFolderId(String FolderId) {
        this.FolderId = FolderId;
    }

    private String DeveloperName;

    @JsonProperty("DeveloperName")
    public String getDeveloperName() {
        return this.DeveloperName;
    }

    @JsonProperty("DeveloperName")
    public void setDeveloperName(String DeveloperName) {
        this.DeveloperName = DeveloperName;
    }

    private String NamespacePrefix;

    @JsonProperty("NamespacePrefix")
    public String getNamespacePrefix() {
        return this.NamespacePrefix;
    }

    @JsonProperty("NamespacePrefix")
    public void setNamespacePrefix(String NamespacePrefix) {
        this.NamespacePrefix = NamespacePrefix;
    }

    private String ContentType;

    @JsonProperty("ContentType")
    public String getContentType() {
        return this.ContentType;
    }

    @JsonProperty("ContentType")
    public void setContentType(String ContentType) {
        this.ContentType = ContentType;
    }

    private String Type;

    @JsonProperty("Type")
    public String getType() {
        return this.Type;
    }

    @JsonProperty("Type")
    public void setType(String Type) {
        this.Type = Type;
    }

    private Boolean IsPublic;

    @JsonProperty("IsPublic")
    public Boolean getIsPublic() {
        return this.IsPublic;
    }

    @JsonProperty("IsPublic")
    public void setIsPublic(Boolean IsPublic) {
        this.IsPublic = IsPublic;
    }

    private Integer BodyLength;

    @JsonProperty("BodyLength")
    public Integer getBodyLength() {
        return this.BodyLength;
    }

    @JsonProperty("BodyLength")
    public void setBodyLength(Integer BodyLength) {
        this.BodyLength = BodyLength;
    }

    // blob field url, use getBlobField to get the content
    @XStreamAlias("Body")
    private String BodyUrl;

    @JsonProperty("Body")
    public String getBodyUrl() {
        return this.BodyUrl;
    }

    @JsonProperty("Body")
    public void setBodyUrl(String BodyUrl) {
        this.BodyUrl = BodyUrl;
    }

    private String Url;

    @JsonProperty("Url")
    public String getUrl() {
        return this.Url;
    }

    @JsonProperty("Url")
    public void setUrl(String Url) {
        this.Url = Url;
    }

    private String Description;

    @JsonProperty("Description")
    public String getDescription() {
        return this.Description;
    }

    @JsonProperty("Description")
    public void setDescription(String Description) {
        this.Description = Description;
    }

    private String Keywords;

    @JsonProperty("Keywords")
    public String getKeywords() {
        return this.Keywords;
    }

    @JsonProperty("Keywords")
    public void setKeywords(String Keywords) {
        this.Keywords = Keywords;
    }

    private Boolean IsInternalUseOnly;

    @JsonProperty("IsInternalUseOnly")
    public Boolean getIsInternalUseOnly() {
        return this.IsInternalUseOnly;
    }

    @JsonProperty("IsInternalUseOnly")
    public void setIsInternalUseOnly(Boolean IsInternalUseOnly) {
        this.IsInternalUseOnly = IsInternalUseOnly;
    }

    private String AuthorId;

    @JsonProperty("AuthorId")
    public String getAuthorId() {
        return this.AuthorId;
    }

    @JsonProperty("AuthorId")
    public void setAuthorId(String AuthorId) {
        this.AuthorId = AuthorId;
    }

    private Boolean IsBodySearchable;

    @JsonProperty("IsBodySearchable")
    public Boolean getIsBodySearchable() {
        return this.IsBodySearchable;
    }

    @JsonProperty("IsBodySearchable")
    public void setIsBodySearchable(Boolean IsBodySearchable) {
        this.IsBodySearchable = IsBodySearchable;
    }

    @Override
    public final SObjectDescription description() {
        return DESCRIPTION;
    }

    private static SObjectDescription createSObjectDescription() {
        final SObjectDescription description = new SObjectDescription();

        final List<SObjectField> fields1 = new ArrayList<>();
        description.setFields(fields1);

        final SObjectField sObjectField1 = createField("Id", "Document ID", "id", "tns:ID", 18, false, false, false, false, false, false, true);
        fields1.add(sObjectField1);
        final SObjectField sObjectField2 = createField("FolderId", "Folder ID", "reference", "tns:ID", 18, false, false, false, false, false, false, false);
        fields1.add(sObjectField2);
        final SObjectField sObjectField3 = createField("IsDeleted", "Deleted", "boolean", "xsd:boolean", 0, false, false, false, false, false, false, false);
        fields1.add(sObjectField3);
        final SObjectField sObjectField4 = createField("Name", "Document Name", "string", "xsd:string", 255, false, false, true, false, false, false, true);
        fields1.add(sObjectField4);
        final SObjectField sObjectField5 = createField("DeveloperName", "Document Unique Name", "string", "xsd:string", 80, false, false, false, false, false, false, false);
        fields1.add(sObjectField5);
        final SObjectField sObjectField6 = createField("NamespacePrefix", "Namespace Prefix", "string", "xsd:string", 15, false, true, false, false, false, false, false);
        fields1.add(sObjectField6);
        final SObjectField sObjectField7 = createField("ContentType", "MIME Type", "string", "xsd:string", 120, false, true, false, false, false, false, false);
        fields1.add(sObjectField7);
        final SObjectField sObjectField8 = createField("Type", "File Extension", "string", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField8);
        final SObjectField sObjectField9 = createField("IsPublic", "Externally Available", "boolean", "xsd:boolean", 0, false, false, false, false, false, false, false);
        fields1.add(sObjectField9);
        final SObjectField sObjectField10 = createField("BodyLength", "Body Length", "int", "xsd:int", 0, false, false, false, false, false, false, false);
        fields1.add(sObjectField10);
        final SObjectField sObjectField11 = createField("Body", "Body", "base64", "xsd:base64Binary", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField11);
        final SObjectField sObjectField12 = createField("Url", "Url", "string", "xsd:string", 255, false, true, false, false, false, false, false);
        fields1.add(sObjectField12);
        final SObjectField sObjectField13 = createField("Description", "Description", "textarea", "xsd:string", 255, false, true, false, false, false, false, false);
        fields1.add(sObjectField13);
        final SObjectField sObjectField14 = createField("Keywords", "Keywords", "string", "xsd:string", 255, false, true, false, false, false, false, false);
        fields1.add(sObjectField14);
        final SObjectField sObjectField15 = createField("IsInternalUseOnly", "Internal Use Only", "boolean", "xsd:boolean", 0, false, false, false, false, false, false, false);
        fields1.add(sObjectField15);
        final SObjectField sObjectField16 = createField("AuthorId", "Author ID", "reference", "tns:ID", 18, false, false, false, false, false, false, false);
        fields1.add(sObjectField16);
        final SObjectField sObjectField17 = createField("CreatedDate", "Created Date", "datetime", "xsd:dateTime", 0, false, false, false, false, false, false, false);
        fields1.add(sObjectField17);
        final SObjectField sObjectField18 = createField("CreatedById", "Created By ID", "reference", "tns:ID", 18, false, false, false, false, false, false, false);
        fields1.add(sObjectField18);
        final SObjectField sObjectField19 = createField("LastModifiedDate", "Last Modified Date", "datetime", "xsd:dateTime", 0, false, false, false, false, false, false, false);
        fields1.add(sObjectField19);
        final SObjectField sObjectField20 = createField("LastModifiedById", "Last Modified By ID", "reference", "tns:ID", 18, false, false, false, false, false, false, false);
        fields1.add(sObjectField20);
        final SObjectField sObjectField21 = createField("SystemModstamp", "System Modstamp", "datetime", "xsd:dateTime", 0, false, false, false, false, false, false, false);
        fields1.add(sObjectField21);
        final SObjectField sObjectField22 = createField("IsBodySearchable", "Document Content Searchable", "boolean", "xsd:boolean", 0, false, false, false, false, false, false,
                                                        false);
        fields1.add(sObjectField22);
        final SObjectField sObjectField23 = createField("LastViewedDate", "Last Viewed Date", "datetime", "xsd:dateTime", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField23);
        final SObjectField sObjectField24 = createField("LastReferencedDate", "Last Referenced Date", "datetime", "xsd:dateTime", 0, false, true, false, false, false, false,
                                                        false);
        fields1.add(sObjectField24);

        description.setLabel("Document");
        description.setLabelPlural("Documents");
        description.setName("Document");

        final SObjectDescriptionUrls sObjectDescriptionUrls1 = new SObjectDescriptionUrls();
        sObjectDescriptionUrls1.setDefaultValues("/services/data/v45.0/sobjects/Document/defaultValues?recordTypeId&fields");
        sObjectDescriptionUrls1.setDescribe("/services/data/v45.0/sobjects/Document/describe");
        sObjectDescriptionUrls1.setRowTemplate("/services/data/v45.0/sobjects/Document/{ID}");
        sObjectDescriptionUrls1.setSobject("/services/data/v45.0/sobjects/Document");
        description.setUrls(sObjectDescriptionUrls1);

        return description;
    }
}

//CHECKSTYLE:ON
