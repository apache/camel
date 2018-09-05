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
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;

/**
 * To use a custom data format implementation that does not come out of the box from Apache Camel.
 *
 * @version
 */
@Metadata(label = "dataformat,transformation", title = "Custom")
@XmlRootElement(name = "customDataFormat")
@XmlAccessorType(XmlAccessType.FIELD)
public class CustomDataFormat extends DataFormatDefinition {
    @XmlAttribute(required = true)
    private String ref;

    public CustomDataFormat() {
    }

    public CustomDataFormat(String ref) {
        this.ref = ref;
    }

    @Override
    protected DataFormat createDataFormat(RouteContext routeContext) {
        return DataFormatDefinition.getDataFormat(routeContext, null, ref);
    }

    /**
     * Reference to the custom {@link org.apache.camel.spi.DataFormat} to lookup from the Camel registry.
     */
    public String getRef() {
        return ref;
    }

    /**
     * Reference to the custom {@link org.apache.camel.spi.DataFormat} to lookup from the Camel registry.
     */
    public void setRef(String ref) {
        this.ref = ref;
    }

    @Override
    public String toString() {
        return "CustomDataFormat[" + ref + "]";
    }
}
