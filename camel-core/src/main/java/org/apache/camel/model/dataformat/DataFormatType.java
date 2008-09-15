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

import java.io.InputStream;
import java.io.OutputStream;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.Exchange;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Represents the base XML type for DataFormat.
 *
 * @version $Revision$
 */
@XmlType(name = "dataFormatType")
@XmlAccessorType(XmlAccessType.FIELD)
public class DataFormatType extends IdentifiedType implements DataFormat {
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

    public static DataFormat getDataFormat(RouteContext routeContext, DataFormatType type, String ref) {
        if (type == null) {
            notNull(ref, "ref or dataFormatType");
            DataFormat dataFormat = routeContext.lookup(ref, DataFormat.class);
            
            if (dataFormat == null) {
                dataFormat = routeContext.getDataFormat(ref);
            }
            
            if (dataFormat instanceof DataFormatType) {
                type = (DataFormatType)dataFormat;
            } else {
                return dataFormat;
            }
        }
        return type.getDataFormat(routeContext);
    }


    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        ObjectHelper.notNull(dataFormat, "dataFormat");
        dataFormat.marshal(exchange, graph, stream);
    }

    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        ObjectHelper.notNull(dataFormat, "dataFormat");
        return dataFormat.unmarshal(exchange, stream);
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
    protected void setProperty(Object bean, String name, Object value) {
        try {
            IntrospectionSupport.setProperty(bean, name, value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to set property " + name + " on " + bean
                                               + ". Reason: " + e, e);
        }
    }


}
