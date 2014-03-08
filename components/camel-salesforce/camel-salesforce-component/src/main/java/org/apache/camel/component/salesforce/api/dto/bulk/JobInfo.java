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
package org.apache.camel.component.salesforce.api.dto.bulk;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;


/**
 * <p>Java class for JobInfo complex type.
 * <p/>
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p/>
 * <pre>
 * &lt;complexType name="JobInfo">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="id" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="operation" type="{http://www.force.com/2009/06/asyncapi/dataload}OperationEnum" minOccurs="0"/>
 *         &lt;element name="object" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="createdById" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="createdDate" type="{http://www.w3.org/2001/XMLSchema}dateTime" minOccurs="0"/>
 *         &lt;element name="systemModstamp" type="{http://www.w3.org/2001/XMLSchema}dateTime" minOccurs="0"/>
 *         &lt;element name="state" type="{http://www.force.com/2009/06/asyncapi/dataload}JobStateEnum" minOccurs="0"/>
 *         &lt;element name="externalIdFieldName" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="concurrencyMode" type="{http://www.force.com/2009/06/asyncapi/dataload}ConcurrencyModeEnum" minOccurs="0"/>
 *         &lt;element name="contentType" type="{http://www.force.com/2009/06/asyncapi/dataload}ContentType" minOccurs="0"/>
 *         &lt;element name="numberBatchesQueued" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/>
 *         &lt;element name="numberBatchesInProgress" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/>
 *         &lt;element name="numberBatchesCompleted" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/>
 *         &lt;element name="numberBatchesFailed" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/>
 *         &lt;element name="numberBatchesTotal" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/>
 *         &lt;element name="numberRecordsProcessed" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/>
 *         &lt;element name="numberRetries" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/>
 *         &lt;element name="apiVersion" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="assignmentRuleId" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="numberRecordsFailed" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/>
 *         &lt;element name="totalProcessingTime" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/>
 *         &lt;element name="apiActiveProcessingTime" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/>
 *         &lt;element name="apexProcessingTime" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "JobInfo", propOrder = {
        "id",
        "operation",
        "object",
        "createdById",
        "createdDate",
        "systemModstamp",
        "state",
        "externalIdFieldName",
        "concurrencyMode",
        "contentType",
        "numberBatchesQueued",
        "numberBatchesInProgress",
        "numberBatchesCompleted",
        "numberBatchesFailed",
        "numberBatchesTotal",
        "numberRecordsProcessed",
        "numberRetries",
        "apiVersion",
        "assignmentRuleId",
        "numberRecordsFailed",
        "totalProcessingTime",
        "apiActiveProcessingTime",
        "apexProcessingTime"
        })
public class JobInfo {

    protected String id;
    protected OperationEnum operation;
    protected String object;
    protected String createdById;
    @XmlSchemaType(name = "dateTime")
    protected XMLGregorianCalendar createdDate;
    @XmlSchemaType(name = "dateTime")
    protected XMLGregorianCalendar systemModstamp;
    protected JobStateEnum state;
    protected String externalIdFieldName;
    protected ConcurrencyModeEnum concurrencyMode;
    protected ContentType contentType;
    protected Integer numberBatchesQueued;
    protected Integer numberBatchesInProgress;
    protected Integer numberBatchesCompleted;
    protected Integer numberBatchesFailed;
    protected Integer numberBatchesTotal;
    protected Integer numberRecordsProcessed;
    protected Integer numberRetries;
    protected String apiVersion;
    protected String assignmentRuleId;
    protected Integer numberRecordsFailed;
    protected Long totalProcessingTime;
    protected Long apiActiveProcessingTime;
    protected Long apexProcessingTime;

    /**
     * Gets the value of the id property.
     *
     * @return possible object is
     *         {@link String }
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the value of the id property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setId(String value) {
        this.id = value;
    }

    /**
     * Gets the value of the operation property.
     *
     * @return possible object is
     *         {@link OperationEnum }
     */
    public OperationEnum getOperation() {
        return operation;
    }

    /**
     * Sets the value of the operation property.
     *
     * @param value allowed object is
     *              {@link OperationEnum }
     */
    public void setOperation(OperationEnum value) {
        this.operation = value;
    }

    /**
     * Gets the value of the object property.
     *
     * @return possible object is
     *         {@link String }
     */
    public String getObject() {
        return object;
    }

    /**
     * Sets the value of the object property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setObject(String value) {
        this.object = value;
    }

    /**
     * Gets the value of the createdById property.
     *
     * @return possible object is
     *         {@link String }
     */
    public String getCreatedById() {
        return createdById;
    }

    /**
     * Sets the value of the createdById property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setCreatedById(String value) {
        this.createdById = value;
    }

    /**
     * Gets the value of the createdDate property.
     *
     * @return possible object is
     *         {@link javax.xml.datatype.XMLGregorianCalendar }
     */
    public XMLGregorianCalendar getCreatedDate() {
        return createdDate;
    }

    /**
     * Sets the value of the createdDate property.
     *
     * @param value allowed object is
     *              {@link javax.xml.datatype.XMLGregorianCalendar }
     */
    public void setCreatedDate(XMLGregorianCalendar value) {
        this.createdDate = value;
    }

    /**
     * Gets the value of the systemModstamp property.
     *
     * @return possible object is
     *         {@link javax.xml.datatype.XMLGregorianCalendar }
     */
    public XMLGregorianCalendar getSystemModstamp() {
        return systemModstamp;
    }

    /**
     * Sets the value of the systemModstamp property.
     *
     * @param value allowed object is
     *              {@link javax.xml.datatype.XMLGregorianCalendar }
     */
    public void setSystemModstamp(XMLGregorianCalendar value) {
        this.systemModstamp = value;
    }

    /**
     * Gets the value of the state property.
     *
     * @return possible object is
     *         {@link JobStateEnum }
     */
    public JobStateEnum getState() {
        return state;
    }

    /**
     * Sets the value of the state property.
     *
     * @param value allowed object is
     *              {@link JobStateEnum }
     */
    public void setState(JobStateEnum value) {
        this.state = value;
    }

    /**
     * Gets the value of the externalIdFieldName property.
     *
     * @return possible object is
     *         {@link String }
     */
    public String getExternalIdFieldName() {
        return externalIdFieldName;
    }

    /**
     * Sets the value of the externalIdFieldName property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setExternalIdFieldName(String value) {
        this.externalIdFieldName = value;
    }

    /**
     * Gets the value of the concurrencyMode property.
     *
     * @return possible object is
     *         {@link ConcurrencyModeEnum }
     */
    public ConcurrencyModeEnum getConcurrencyMode() {
        return concurrencyMode;
    }

    /**
     * Sets the value of the concurrencyMode property.
     *
     * @param value allowed object is
     *              {@link ConcurrencyModeEnum }
     */
    public void setConcurrencyMode(ConcurrencyModeEnum value) {
        this.concurrencyMode = value;
    }

    /**
     * Gets the value of the contentType property.
     *
     * @return possible object is
     *         {@link ContentType }
     */
    public ContentType getContentType() {
        return contentType;
    }

    /**
     * Sets the value of the contentType property.
     *
     * @param value allowed object is
     *              {@link ContentType }
     */
    public void setContentType(ContentType value) {
        this.contentType = value;
    }

    /**
     * Gets the value of the numberBatchesQueued property.
     *
     * @return possible object is
     *         {@link Integer }
     */
    public Integer getNumberBatchesQueued() {
        return numberBatchesQueued;
    }

    /**
     * Sets the value of the numberBatchesQueued property.
     *
     * @param value allowed object is
     *              {@link Integer }
     */
    public void setNumberBatchesQueued(Integer value) {
        this.numberBatchesQueued = value;
    }

    /**
     * Gets the value of the numberBatchesInProgress property.
     *
     * @return possible object is
     *         {@link Integer }
     */
    public Integer getNumberBatchesInProgress() {
        return numberBatchesInProgress;
    }

    /**
     * Sets the value of the numberBatchesInProgress property.
     *
     * @param value allowed object is
     *              {@link Integer }
     */
    public void setNumberBatchesInProgress(Integer value) {
        this.numberBatchesInProgress = value;
    }

    /**
     * Gets the value of the numberBatchesCompleted property.
     *
     * @return possible object is
     *         {@link Integer }
     */
    public Integer getNumberBatchesCompleted() {
        return numberBatchesCompleted;
    }

    /**
     * Sets the value of the numberBatchesCompleted property.
     *
     * @param value allowed object is
     *              {@link Integer }
     */
    public void setNumberBatchesCompleted(Integer value) {
        this.numberBatchesCompleted = value;
    }

    /**
     * Gets the value of the numberBatchesFailed property.
     *
     * @return possible object is
     *         {@link Integer }
     */
    public Integer getNumberBatchesFailed() {
        return numberBatchesFailed;
    }

    /**
     * Sets the value of the numberBatchesFailed property.
     *
     * @param value allowed object is
     *              {@link Integer }
     */
    public void setNumberBatchesFailed(Integer value) {
        this.numberBatchesFailed = value;
    }

    /**
     * Gets the value of the numberBatchesTotal property.
     *
     * @return possible object is
     *         {@link Integer }
     */
    public Integer getNumberBatchesTotal() {
        return numberBatchesTotal;
    }

    /**
     * Sets the value of the numberBatchesTotal property.
     *
     * @param value allowed object is
     *              {@link Integer }
     */
    public void setNumberBatchesTotal(Integer value) {
        this.numberBatchesTotal = value;
    }

    /**
     * Gets the value of the numberRecordsProcessed property.
     *
     * @return possible object is
     *         {@link Integer }
     */
    public Integer getNumberRecordsProcessed() {
        return numberRecordsProcessed;
    }

    /**
     * Sets the value of the numberRecordsProcessed property.
     *
     * @param value allowed object is
     *              {@link Integer }
     */
    public void setNumberRecordsProcessed(Integer value) {
        this.numberRecordsProcessed = value;
    }

    /**
     * Gets the value of the numberRetries property.
     *
     * @return possible object is
     *         {@link Integer }
     */
    public Integer getNumberRetries() {
        return numberRetries;
    }

    /**
     * Sets the value of the numberRetries property.
     *
     * @param value allowed object is
     *              {@link Integer }
     */
    public void setNumberRetries(Integer value) {
        this.numberRetries = value;
    }

    /**
     * Gets the value of the apiVersion property.
     *
     * @return possible object is
     *         {@link String }
     */
    public String getApiVersion() {
        return apiVersion;
    }

    /**
     * Sets the value of the apiVersion property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setApiVersion(String value) {
        this.apiVersion = value;
    }

    /**
     * Gets the value of the assignmentRuleId property.
     *
     * @return possible object is
     *         {@link String }
     */
    public String getAssignmentRuleId() {
        return assignmentRuleId;
    }

    /**
     * Sets the value of the assignmentRuleId property.
     *
     * @param value allowed object is
     *              {@link String }
     */
    public void setAssignmentRuleId(String value) {
        this.assignmentRuleId = value;
    }

    /**
     * Gets the value of the numberRecordsFailed property.
     *
     * @return possible object is
     *         {@link Integer }
     */
    public Integer getNumberRecordsFailed() {
        return numberRecordsFailed;
    }

    /**
     * Sets the value of the numberRecordsFailed property.
     *
     * @param value allowed object is
     *              {@link Integer }
     */
    public void setNumberRecordsFailed(Integer value) {
        this.numberRecordsFailed = value;
    }

    /**
     * Gets the value of the totalProcessingTime property.
     *
     * @return possible object is
     *         {@link Long }
     */
    public Long getTotalProcessingTime() {
        return totalProcessingTime;
    }

    /**
     * Sets the value of the totalProcessingTime property.
     *
     * @param value allowed object is
     *              {@link Long }
     */
    public void setTotalProcessingTime(Long value) {
        this.totalProcessingTime = value;
    }

    /**
     * Gets the value of the apiActiveProcessingTime property.
     *
     * @return possible object is
     *         {@link Long }
     */
    public Long getApiActiveProcessingTime() {
        return apiActiveProcessingTime;
    }

    /**
     * Sets the value of the apiActiveProcessingTime property.
     *
     * @param value allowed object is
     *              {@link Long }
     */
    public void setApiActiveProcessingTime(Long value) {
        this.apiActiveProcessingTime = value;
    }

    /**
     * Gets the value of the apexProcessingTime property.
     *
     * @return possible object is
     *         {@link Long }
     */
    public Long getApexProcessingTime() {
        return apexProcessingTime;
    }

    /**
     * Sets the value of the apexProcessingTime property.
     *
     * @param value allowed object is
     *              {@link Long }
     */
    public void setApexProcessingTime(Long value) {
        this.apexProcessingTime = value;
    }

}
