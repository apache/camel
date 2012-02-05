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

/**
 * Represents the BeanIO {@link org.apache.camel.spi.DataFormat}
 *
 * @version 
 */
@XmlRootElement(name = "beanio")
@XmlAccessorType(XmlAccessType.FIELD)
public class BeanioDataFormat extends DataFormatDefinition {

    @XmlAttribute(required = true)
    private String mapping;
    @XmlAttribute(required = true)
    private String streamName;
    @XmlAttribute
    private Boolean ignoreUnidentifiedRecords;
    @XmlAttribute
    private Boolean ignoreUnexpectedRecords;
    @XmlAttribute
    private Boolean ignoreInvalidRecords;
    @XmlAttribute
    private String encoding;

    public BeanioDataFormat() {
        super("beanio");
    }

    @Override
    protected void configureDataFormat(DataFormat dataFormat) {
        setProperty(dataFormat, "mapping", mapping);
        setProperty(dataFormat, "streamName", streamName);
        if (ignoreUnidentifiedRecords != null) {
            setProperty(dataFormat, "ignoreUnidentifiedRecords", ignoreUnidentifiedRecords);
        }
        if (ignoreUnexpectedRecords != null) {
            setProperty(dataFormat, "ignoreUnexpectedRecords", ignoreUnexpectedRecords);
        }
        if (ignoreInvalidRecords != null) {
            setProperty(dataFormat, "ignoreInvalidRecords", ignoreInvalidRecords);
        }
        setProperty(dataFormat, "encoding", encoding);
    }

}