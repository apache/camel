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
import org.apache.camel.spi.RouteContext;

/**
 * Represents the Bindy {@link org.apache.camel.spi.DataFormat}
 *
 * @version 
 */
@XmlRootElement(name = "bindy")
@XmlAccessorType(XmlAccessType.FIELD)
public class BindyDataFormat extends DataFormatDefinition {
    @XmlAttribute(required = true)
    private BindyType type;
    @XmlAttribute(required = true)
    private String[] packages;
    @XmlAttribute
    private String locale;

    public BindyDataFormat() {
    }

    public BindyType getType() {
        return type;
    }

    public void setType(BindyType type) {
        this.type = type;
    }

    public String[] getPackages() {
        return packages;
    }

    public void setPackages(String[] packages) {
        this.packages = packages;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    protected DataFormat createDataFormat(RouteContext routeContext) {
        if (type == BindyType.Csv) {
            setDataFormatName("bindy-csv");
        } else {
            setDataFormatName("bindy-kvp");
        }
        return super.createDataFormat(routeContext);
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat) {
        setProperty(dataFormat, "packages", packages);
        setProperty(dataFormat, "locale", locale);
    }

}