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

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;

/**
 * <p>
 * Java class for BatchStateEnum.
 * <p/>
 * <p>
 * The following schema fragment specifies the expected content contained within
 * this class.
 * <p/>
 * 
 * <pre>
 * &lt;simpleType name="BatchStateEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="Queued"/>
 *     &lt;enumeration value="InProgress"/>
 *     &lt;enumeration value="Completed"/>
 *     &lt;enumeration value="Failed"/>
 *     &lt;enumeration value="NotProcessed"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 */
@XmlType(name = "BatchStateEnum")
@XmlEnum
public enum BatchStateEnum {

    @XmlEnumValue("Queued")
    QUEUED("Queued"), @XmlEnumValue("InProgress")
    IN_PROGRESS("InProgress"), @XmlEnumValue("Completed")
    COMPLETED("Completed"), @XmlEnumValue("Failed")
    FAILED("Failed"), @XmlEnumValue("NotProcessed")
    NOT_PROCESSED("NotProcessed");
    private final String value;

    BatchStateEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static BatchStateEnum fromValue(String v) {
        for (BatchStateEnum c : BatchStateEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
