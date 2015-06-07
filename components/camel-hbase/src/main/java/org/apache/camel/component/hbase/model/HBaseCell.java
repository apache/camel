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
package org.apache.camel.component.hbase.model;

import javax.xml.bind.annotation.XmlAttribute;

/**
 * A simplified representation of HBase KeyValue objects, which uses the actual Objects instead of byte arrays.
 * It is used in order to abstract the conversion strategy from CellMappingStrategy.
 * It is also used as a template to specify which will be the columns returned in gets, scans etc.
 */
public class HBaseCell {

    private String family;
    private String qualifier;
    private Object value;
    private Long timestamp;
    //The value type can be optionally specified for Gets and Scan, to specify how the byte[] read will be converted.
    private Class<?> valueType = String.class;

    public String toString() {
        return "HBaseCell=[family=" + family + ", qualifier=" + qualifier + ", value=" + value + ", valueType=" + valueType.getName();
    }

    @XmlAttribute(name = "family")
    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    @XmlAttribute(name = "qualifier")
    public String getQualifier() {
        return qualifier;
    }

    public void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    @XmlAttribute(name = "type")
    public Class<?> getValueType() {
        return valueType;
    }

    public void setValueType(Class<?> valueType) {
        if (valueType == null) {
            throw new IllegalArgumentException("Value type can not be null");
        }
        this.valueType = valueType;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        HBaseCell hBaseCell = (HBaseCell) o;

        if (family != null ? !family.equals(hBaseCell.family) : hBaseCell.family != null) {
            return false;
        }
        if (qualifier != null ? !qualifier.equals(hBaseCell.qualifier) : hBaseCell.qualifier != null) {
            return false;
        }
        if (timestamp != null ? !timestamp.equals(hBaseCell.timestamp) : hBaseCell.timestamp != null) {
            return false;
        }
        if (value != null ? !value.equals(hBaseCell.value) : hBaseCell.value != null) {
            return false;
        }
        if (valueType != null ? !valueType.equals(hBaseCell.valueType) : hBaseCell.valueType != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = family != null ? family.hashCode() : 0;
        result = 31 * result + (qualifier != null ? qualifier.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (timestamp != null ? timestamp.hashCode() : 0);
        result = 31 * result + (valueType != null ? valueType.hashCode() : 0);
        return result;
    }
}
