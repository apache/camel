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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.builder.DataFormatBuilder;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Marshal and unmarshal Java beans to and from flat files (such as CSV, delimited, or fixed length formats).
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
    @Metadata(label = "advanced")
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

    private BeanioDataFormat(BeanioDataFormat.Builder builder) {
        this();
        this.mapping = builder.mapping;
        this.streamName = builder.streamName;
        this.ignoreUnidentifiedRecords = builder.ignoreUnidentifiedRecords;
        this.ignoreUnexpectedRecords = builder.ignoreUnexpectedRecords;
        this.ignoreInvalidRecords = builder.ignoreInvalidRecords;
        this.encoding = builder.encoding;
        this.beanReaderErrorHandlerType = builder.beanReaderErrorHandlerType;
        this.unmarshalSingleObject = builder.unmarshalSingleObject;
    }

    public String getMapping() {
        return mapping;
    }

    /**
     * The BeanIO mapping file. Is by default loaded from the classpath. You can prefix with file:, http:, or classpath:
     * to denote from where to load the mapping file.
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
     * To use a custom org.apache.camel.dataformat.beanio.BeanIOErrorHandler as error handler while parsing. Configure
     * the fully qualified class name of the error handler. Notice the options ignoreUnidentifiedRecords,
     * ignoreUnexpectedRecords, and ignoreInvalidRecords may not be in use when you use a custom error handler.
     */
    public void setBeanReaderErrorHandlerType(String beanReaderErrorHandlerType) {
        this.beanReaderErrorHandlerType = beanReaderErrorHandlerType;
    }

    public String getUnmarshalSingleObject() {
        return unmarshalSingleObject;
    }

    /**
     * This options controls whether to unmarshal as a list of objects or as a single object only. The former is the
     * default mode, and the latter is only intended in special use-cases where beanio maps the Camel message to a
     * single POJO bean.
     */
    public void setUnmarshalSingleObject(String unmarshalSingleObject) {
        this.unmarshalSingleObject = unmarshalSingleObject;
    }

    /**
     * {@code Builder} is a specific builder for {@link BeanioDataFormat}.
     */
    @XmlTransient
    public static class Builder implements DataFormatBuilder<BeanioDataFormat> {

        private String mapping;
        private String streamName;
        private String ignoreUnidentifiedRecords;
        private String ignoreUnexpectedRecords;
        private String ignoreInvalidRecords;
        private String encoding;
        private String beanReaderErrorHandlerType;
        private String unmarshalSingleObject;

        /**
         * The BeanIO mapping file. Is by default loaded from the classpath. You can prefix with file:, http:, or
         * classpath: to denote from where to load the mapping file.
         */
        public BeanioDataFormat.Builder mapping(String mapping) {
            this.mapping = mapping;
            return this;
        }

        /**
         * The name of the stream to use.
         */
        public BeanioDataFormat.Builder streamName(String streamName) {
            this.streamName = streamName;
            return this;
        }

        /**
         * Whether to ignore unidentified records.
         */
        public BeanioDataFormat.Builder ignoreUnidentifiedRecords(String ignoreUnidentifiedRecords) {
            this.ignoreUnidentifiedRecords = ignoreUnidentifiedRecords;
            return this;
        }

        /**
         * Whether to ignore unidentified records.
         */
        public BeanioDataFormat.Builder ignoreUnidentifiedRecords(boolean ignoreUnidentifiedRecords) {
            this.ignoreUnidentifiedRecords = ignoreUnidentifiedRecords ? "true" : "false";
            return this;
        }

        /**
         * Whether to ignore unexpected records.
         */
        public BeanioDataFormat.Builder ignoreUnexpectedRecords(String ignoreUnexpectedRecords) {
            this.ignoreUnexpectedRecords = ignoreUnexpectedRecords;
            return this;
        }

        /**
         * Whether to ignore unexpected records.
         */
        public BeanioDataFormat.Builder ignoreUnexpectedRecords(boolean ignoreUnexpectedRecords) {
            this.ignoreUnexpectedRecords = ignoreUnexpectedRecords ? "true" : "false";
            return this;
        }

        /**
         * Whether to ignore invalid records.
         */
        public BeanioDataFormat.Builder ignoreInvalidRecords(String ignoreInvalidRecords) {
            this.ignoreInvalidRecords = ignoreInvalidRecords;
            return this;
        }

        /**
         * Whether to ignore invalid records.
         */
        public BeanioDataFormat.Builder ignoreInvalidRecords(boolean ignoreInvalidRecords) {
            this.ignoreInvalidRecords = ignoreInvalidRecords ? "true" : "false";
            return this;
        }

        /**
         * The charset to use.
         * <p/>
         * Is by default the JVM platform default charset.
         */
        public BeanioDataFormat.Builder encoding(String encoding) {
            this.encoding = encoding;
            return this;
        }

        /**
         * To use a custom org.apache.camel.dataformat.beanio.BeanIOErrorHandler as error handler while parsing.
         * Configure the fully qualified class name of the error handler. Notice the options ignoreUnidentifiedRecords,
         * ignoreUnexpectedRecords, and ignoreInvalidRecords may not be in use when you use a custom error handler.
         */
        public BeanioDataFormat.Builder beanReaderErrorHandlerType(String beanReaderErrorHandlerType) {
            this.beanReaderErrorHandlerType = beanReaderErrorHandlerType;
            return this;
        }

        /**
         * This options controls whether to unmarshal as a list of objects or as a single object only. The former is the
         * default mode, and the latter is only intended in special use-cases where beanio maps the Camel message to a
         * single POJO bean.
         */
        public BeanioDataFormat.Builder unmarshalSingleObject(String unmarshalSingleObject) {
            this.unmarshalSingleObject = unmarshalSingleObject;
            return this;
        }

        /**
         * This options controls whether to unmarshal as a list of objects or as a single object only. The former is the
         * default mode, and the latter is only intended in special use-cases where beanio maps the Camel message to a
         * single POJO bean.
         */
        public BeanioDataFormat.Builder unmarshalSingleObject(boolean unmarshalSingleObject) {
            this.unmarshalSingleObject = unmarshalSingleObject ? "true" : "false";
            return this;
        }

        @Override
        public BeanioDataFormat end() {
            return new BeanioDataFormat(this);
        }
    }

}
