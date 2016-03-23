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

import org.apache.camel.CamelContext;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Metadata;

/**
 * BeanIO data format
 *
 * @version 
 */
@Metadata(label = "dataformat,transformation,csv", title = "BeanIO")
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
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        setProperty(camelContext, dataFormat, "mapping", mapping);
        setProperty(camelContext, dataFormat, "streamName", streamName);
        if (ignoreUnidentifiedRecords != null) {
            setProperty(camelContext, dataFormat, "ignoreUnidentifiedRecords", ignoreUnidentifiedRecords);
        }
        if (ignoreUnexpectedRecords != null) {
            setProperty(camelContext, dataFormat, "ignoreUnexpectedRecords", ignoreUnexpectedRecords);
        }
        if (ignoreInvalidRecords != null) {
            setProperty(camelContext, dataFormat, "ignoreInvalidRecords", ignoreInvalidRecords);
        }
        if (encoding != null) {
            setProperty(camelContext, dataFormat, "encoding", encoding);
        }
    }

    public String getMapping() {
        return mapping;
    }

    /**
     * The BeanIO mapping file.
     * Is by default loaded from the classpath. You can prefix with file:, http:, or classpath: to denote from where to load the mapping file.
     */
    public void setMapping(String mapping) {
        this.mapping = mapping;
    }

    public String getStreamName() {
        return streamName;
    }

    /**
     * The name of the stream to use.
     */
    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public Boolean getIgnoreUnidentifiedRecords() {
        return ignoreUnidentifiedRecords;
    }

    /**
     * Whether to ignore unidentified records.
     */
    public void setIgnoreUnidentifiedRecords(Boolean ignoreUnidentifiedRecords) {
        this.ignoreUnidentifiedRecords = ignoreUnidentifiedRecords;
    }

    public Boolean getIgnoreUnexpectedRecords() {
        return ignoreUnexpectedRecords;
    }

    /**
     * Whether to ignore unexpected records.
     */
    public void setIgnoreUnexpectedRecords(Boolean ignoreUnexpectedRecords) {
        this.ignoreUnexpectedRecords = ignoreUnexpectedRecords;
    }

    public Boolean getIgnoreInvalidRecords() {
        return ignoreInvalidRecords;
    }

    /**
     * Whether to ignore invalid records.
     */
    public void setIgnoreInvalidRecords(Boolean ignoreInvalidRecords) {
        this.ignoreInvalidRecords = ignoreInvalidRecords;
    }

    public String getEncoding() {
        return encoding;
    }

    /**
     * The charset to use.
     * <p/>
     * Is by default the JVM platform default charset.
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

}
