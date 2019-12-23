/*
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
import org.apache.camel.spi.Metadata;

/**
 * The BeanIO data format is used for working with flat payloads (such as CSV,
 * delimited, or fixed length formats).
 */
@Metadata(firstVersion = "2.10.0", label = "dataformat,transformation,csv", title = "BeanIO")
@XmlRootElement(name = "beanio")
@XmlAccessorType(XmlAccessType.FIELD)
public class BeanioDataFormat extends DataFormatDefinition {

    @XmlAttribute(required = true)
    private String mapping;
    @XmlAttribute(required = true)
    private String streamName;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String ignoreUnidentifiedRecords;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String ignoreUnexpectedRecords;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String ignoreInvalidRecords;
    @XmlAttribute
    private String encoding;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String beanReaderErrorHandlerType;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String unmarshalSingleObject;

    public BeanioDataFormat() {
        super("beanio");
    }

    public String getMapping() {
        return mapping;
    }

    /**
     * The BeanIO mapping file. Is by default loaded from the classpath. You can
     * prefix with file:, http:, or classpath: to denote from where to load the
     * mapping file.
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

    public String getIgnoreUnidentifiedRecords() {
        return ignoreUnidentifiedRecords;
    }

    /**
     * Whether to ignore unidentified records.
     */
    public void setIgnoreUnidentifiedRecords(String ignoreUnidentifiedRecords) {
        this.ignoreUnidentifiedRecords = ignoreUnidentifiedRecords;
    }

    public String getIgnoreUnexpectedRecords() {
        return ignoreUnexpectedRecords;
    }

    /**
     * Whether to ignore unexpected records.
     */
    public void setIgnoreUnexpectedRecords(String ignoreUnexpectedRecords) {
        this.ignoreUnexpectedRecords = ignoreUnexpectedRecords;
    }

    public String getIgnoreInvalidRecords() {
        return ignoreInvalidRecords;
    }

    /**
     * Whether to ignore invalid records.
     */
    public void setIgnoreInvalidRecords(String ignoreInvalidRecords) {
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

    public String getBeanReaderErrorHandlerType() {
        return beanReaderErrorHandlerType;
    }

    /**
     * To use a custom org.apache.camel.dataformat.beanio.BeanIOErrorHandler as
     * error handler while parsing. Configure the fully qualified class name of
     * the error handler. Notice the options ignoreUnidentifiedRecords,
     * ignoreUnexpectedRecords, and ignoreInvalidRecords may not be in use when
     * you use a custom error handler.
     */
    public void setBeanReaderErrorHandlerType(String beanReaderErrorHandlerType) {
        this.beanReaderErrorHandlerType = beanReaderErrorHandlerType;
    }

    public String getUnmarshalSingleObject() {
        return unmarshalSingleObject;
    }

    /**
     * This options controls whether to unmarshal as a list of objects or as a
     * single object only. The former is the default mode, and the latter is
     * only intended in special use-cases where beanio maps the Camel message to
     * a single POJO bean.
     */
    public void setUnmarshalSingleObject(String unmarshalSingleObject) {
        this.unmarshalSingleObject = unmarshalSingleObject;
    }
}
