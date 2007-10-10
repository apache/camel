/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.model.dataformat;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.impl.RouteContext;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.IntrospectionSupport;

/**
 * @version $Revision: 1.1 $
 */
@XmlType(name = "dataFormatType")
@XmlAccessorType(XmlAccessType.FIELD)
public class DataFormatType {
    @XmlTransient
    private DataFormat dataFormat;
    @XmlTransient
    private String dataFormatTypeName;

    public DataFormatType() {
    }

    public DataFormatType(DataFormat dataFormat) {
        this.dataFormat = dataFormat;
    }

    protected DataFormatType(String dataFormatTypeName) {
        this.dataFormatTypeName = dataFormatTypeName;
    }

    public DataFormat getDataFormat(RouteContext routeContext) {
        if (dataFormat == null) {
            dataFormat = createDataFormat(routeContext);
            ObjectHelper.notNull(dataFormat, "dataFormat");
            configureDataFormat(dataFormat);
        }
        return dataFormat;
    }

    /**
     * Factory method to create the data format instance
     */
    protected DataFormat createDataFormat(RouteContext routeContext) {
        if (dataFormatTypeName != null) {
            Class type = ObjectHelper.loadClass(dataFormatTypeName, getClass().getClassLoader());
            if (type == null) {
                throw new IllegalArgumentException("The class " + dataFormatTypeName + " is not on the classpath! Cannot use the dataFormat " + this);
            }
            return (DataFormat) ObjectHelper.newInstance(type);
        }
        return null;
    }

    /**
     * Allows derived classes to customize the data format
     */
    protected void configureDataFormat(DataFormat dataFormat) {
    }

    /**
     * Sets a named property on the data format instance using introspection
     */
    protected void setProperty(DataFormat dataFormat, String name, Object value) {
        try {
            IntrospectionSupport.setProperty(dataFormat,name, value);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Failed to set property " + name + " on " + dataFormat + ". Reason: " + e, e);
        }

    }
}
