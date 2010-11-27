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
 * Represents the JiBX XML {@link org.apache.camel.spi.DataFormat}
 *
 */
@XmlRootElement(name = "jibx")
@XmlAccessorType(XmlAccessType.NONE)
public class JibxDataFormat extends DataFormatDefinition {

    @XmlAttribute
    private Class unmarshallClass;

    public JibxDataFormat() {
        super("jibx");
    }

    public JibxDataFormat(Class unmarshallClass) {
        this();
        setUnmarshallClass(unmarshallClass);
    }

    @Override
    protected DataFormat createDataFormat(RouteContext routeContext) {
        DataFormat answer = super.createDataFormat(routeContext);
        if (unmarshallClass != null) {
            setProperty(answer, "unmarshallClass", unmarshallClass);
        }
        return answer;
    }

    public Class getUnmarshallClass() {
        return unmarshallClass;
    }

    public void setUnmarshallClass(Class unmarshallClass) {
        this.unmarshallClass = unmarshallClass;
    }

}