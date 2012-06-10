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
    //The value type can be optionally specified for Gets and Scan, to specify how the byte[] read will be converted.
    private Class<?> valueType = String.class;

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
        this.valueType = valueType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        HBaseCell cell = (HBaseCell) o;

        if (family != null ? !family.equals(cell.family) : cell.family != null) {
            return false;
        }
        if (qualifier != null ? !qualifier.equals(cell.qualifier) : cell.qualifier != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = family != null ? family.hashCode() : 0;
        result = 31 * result + (qualifier != null ? qualifier.hashCode() : 0);
        return result;
    }
}
