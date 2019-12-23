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
package org.apache.camel.component.salesforce.api.dto.bulk;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;

/**
 * <p>
 * Java class for BatchInfo complex type.
 * <p/>
 * <p>
 * The following schema fragment specifies the expected content contained within
 * this class.
 * <p/>
 * 
 * <pre>
 * &lt;complexType name="BatchInfo">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="id" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="jobId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="state" type="{http://www.force.com/2009/06/asyncapi/dataload}BatchStateEnum"/>
 *         &lt;element name="stateMessage" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="createdDate" type="{http://www.w3.org/2001/XMLSchema}dateTime"/>
 *         &lt;element name="systemModstamp" type="{http://www.w3.org/2001/XMLSchema}dateTime" minOccurs="0"/>
 *         &lt;element name="numberRecordsProcessed" type="{http://www.w3.org/2001/XMLSchema}int"/>
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
@XmlType(name = "BatchInfo", propOrder = {"id", "jobId", "state", "stateMessage", "createdDate", "systemModstamp", "numberRecordsProcessed", "numberRecordsFailed",
                                          "totalProcessingTime", "apiActiveProcessingTime", "apexProcessingTime"})
public class BatchInfo {

    @XmlElement(required = true)
    protected String id;
    @XmlElement(required = true)
    protected String jobId;
    @XmlElement(required = true)
    protected BatchStateEnum state;
    protected String stateMessage;
    @XmlElement(required = true)
    @XmlSchemaType(name = "dateTime")
    protected XMLGregorianCalendar createdDate;
    @XmlSchemaType(name = "dateTime")
    protected XMLGregorianCalendar systemModstamp;
    protected int numberRecordsProcessed;
    protected Integer numberRecordsFailed;
    protected Long totalProcessingTime;
    protected Long apiActiveProcessingTime;
    protected Long apexProcessingTime;

    /**
     * Gets the value of the id property.
     *
     * @return possible object is {@link String }
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the value of the id property.
     *
     * @param value allowed object is {@link String }
     */
    public void setId(String value) {
        this.id = value;
    }

    /**
     * Gets the value of the jobId property.
     *
     * @return possible object is {@link String }
     */
    public String getJobId() {
        return jobId;
    }

    /**
     * Sets the value of the jobId property.
     *
     * @param value allowed object is {@link String }
     */
    public void setJobId(String value) {
        this.jobId = value;
    }

    /**
     * Gets the value of the state property.
     *
     * @return possible object is {@link BatchStateEnum }
     */
    public BatchStateEnum getState() {
        return state;
    }

    /**
     * Sets the value of the state property.
     *
     * @param value allowed object is {@link BatchStateEnum }
     */
    public void setState(BatchStateEnum value) {
        this.state = value;
    }

    /**
     * Gets the value of the stateMessage property.
     *
     * @return possible object is {@link String }
     */
    public String getStateMessage() {
        return stateMessage;
    }

    /**
     * Sets the value of the stateMessage property.
     *
     * @param value allowed object is {@link String }
     */
    public void setStateMessage(String value) {
        this.stateMessage = value;
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
     *            {@link javax.xml.datatype.XMLGregorianCalendar }
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
     *            {@link javax.xml.datatype.XMLGregorianCalendar }
     */
    public void setSystemModstamp(XMLGregorianCalendar value) {
        this.systemModstamp = value;
    }

    /**
     * Gets the value of the numberRecordsProcessed property.
     */
    public int getNumberRecordsProcessed() {
        return numberRecordsProcessed;
    }

    /**
     * Sets the value of the numberRecordsProcessed property.
     */
    public void setNumberRecordsProcessed(int value) {
        this.numberRecordsProcessed = value;
    }

    /**
     * Gets the value of the numberRecordsFailed property.
     *
     * @return possible object is {@link Integer }
     */
    public Integer getNumberRecordsFailed() {
        return numberRecordsFailed;
    }

    /**
     * Sets the value of the numberRecordsFailed property.
     *
     * @param value allowed object is {@link Integer }
     */
    public void setNumberRecordsFailed(Integer value) {
        this.numberRecordsFailed = value;
    }

    /**
     * Gets the value of the totalProcessingTime property.
     *
     * @return possible object is {@link Long }
     */
    public Long getTotalProcessingTime() {
        return totalProcessingTime;
    }

    /**
     * Sets the value of the totalProcessingTime property.
     *
     * @param value allowed object is {@link Long }
     */
    public void setTotalProcessingTime(Long value) {
        this.totalProcessingTime = value;
    }

    /**
     * Gets the value of the apiActiveProcessingTime property.
     *
     * @return possible object is {@link Long }
     */
    public Long getApiActiveProcessingTime() {
        return apiActiveProcessingTime;
    }

    /**
     * Sets the value of the apiActiveProcessingTime property.
     *
     * @param value allowed object is {@link Long }
     */
    public void setApiActiveProcessingTime(Long value) {
        this.apiActiveProcessingTime = value;
    }

    /**
     * Gets the value of the apexProcessingTime property.
     *
     * @return possible object is {@link Long }
     */
    public Long getApexProcessingTime() {
        return apexProcessingTime;
    }

    /**
     * Sets the value of the apexProcessingTime property.
     *
     * @param value allowed object is {@link Long }
     */
    public void setApexProcessingTime(Long value) {
        this.apexProcessingTime = value;
    }

}
