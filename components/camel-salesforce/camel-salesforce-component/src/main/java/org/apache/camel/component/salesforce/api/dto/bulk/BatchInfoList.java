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

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;

/**
 * <p>
 * Java class for BatchInfoList complex type.
 * <p/>
 * <p>
 * The following schema fragment specifies the expected content contained within
 * this class.
 * <p/>
 * 
 * <pre>
 * &lt;complexType name="BatchInfoList">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="batchInfo" type="{http://www.force.com/2009/06/asyncapi/dataload}BatchInfo" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "BatchInfoList", propOrder = {"batchInfo"})
public class BatchInfoList {

    protected List<BatchInfo> batchInfo;

    /**
     * Gets the value of the batchInfo property.
     * <p/>
     * <p/>
     * This accessor method returns a reference to the live list, not a
     * snapshot. Therefore any modification you make to the returned list will
     * be present inside the JAXB object. This is why there is not a
     * <CODE>set</CODE> method for the batchInfo property.
     * <p/>
     * <p/>
     * For example, to add a new item, do as follows:
     * 
     * <pre>
     * getBatchInfo().add(newItem);
     * </pre>
     * <p/>
     * <p/>
     * <p/>
     * Objects of the following type(s) are allowed in the list {@link BatchInfo
     * }
     */
    public List<BatchInfo> getBatchInfo() {
        if (batchInfo == null) {
            batchInfo = new ArrayList<>();
        }
        return this.batchInfo;
    }

}
