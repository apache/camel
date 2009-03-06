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
package org.apache.camel.model.dataformat;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.model.IdentifiedType;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents the base XML type for DataFormat.
 *
 * @version $Revision$
 */
@XmlType(name = "dataFormat")
@XmlAccessorType(XmlAccessType.FIELD)
public class DataFormatDefinition extends IdentifiedType {
    @XmlTransient
    private DataFormat dataFormat;
    @XmlTransient
    private String dataFormatName;

    public DataFormatDefinition() {
    }

    public DataFormatDefinition(DataFormat dataFormat) {
        this.dataFormat = dataFormat;
    }

    protected DataFormatDefinition(String dataFormatName) {
        this.dataFormatName = dataFormatName;
    }

    /**
     * Factory method to create the data format
     * @param routeContext route context
     * @param type the data format type
     * @param ref  reference to lookup for a data format
     * @return the data format or null if not possible to create
     */
    public static DataFormat getDataFormat(RouteContext routeContext, DataFormatDefinition type, String ref) {
        if (type == null) {
            ObjectHelper.notNull(ref, "ref or dataFormat");

            DataFormat dataFormat = lookup(routeContext, ref, DataFormat.class);
            if (dataFormat == null) {
                // lookup type and create the data format from it
                type = lookup(routeContext, ref, DataFormatDefinition.class);
                if (type == null) {
                    type = routeContext.getDataFormat(ref);
                }
                if (type != null) {
                    dataFormat = type.getDataFormat(routeContext);
                }
            }

            if (dataFormat == null) {
                throw new IllegalArgumentException("Cannot find data format in registry with ref: " + ref);
            }

            return dataFormat;
        } else {
            return type.getDataFormat(routeContext);
        }
    }

    private static <T> T lookup(RouteContext routeContext, String ref, Class<T> type) {
        try {
            return routeContext.lookup(ref, type);
        } catch (Exception e) {
            // need to ignore not same type and return it as null
            return null;
        }
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
    @SuppressWarnings("unchecked")
    protected DataFormat createDataFormat(RouteContext routeContext) {
        if (dataFormatName != null) {
            Class type = routeContext.getCamelContext().getClassResolver().resolveClass(dataFormatName);
            if (type == null) {
                throw new IllegalArgumentException("The class " + dataFormatName + " is not on the classpath! Cannot use the dataFormat " + this);
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
    protected void setProperty(Object bean, String name, Object value) {
        try {
            IntrospectionSupport.setProperty(bean, name, value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to set property: " + name + " on: " + bean + ". Reason: " + e, e);
        }
    }
}

