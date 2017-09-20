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
package org.apache.camel.dataformat.beanio;

import java.nio.charset.Charset;
import java.util.Properties;

import org.beanio.BeanReaderErrorHandler;

/**
 * To configure the BeanIO data format, or BeanIO splitter.
 */
public class BeanIOConfiguration {

    private String streamName;
    private String mapping;
    private boolean ignoreUnidentifiedRecords;
    private boolean ignoreUnexpectedRecords;
    private boolean ignoreInvalidRecords;
    private Charset encoding = Charset.defaultCharset();
    private Properties properties;
    private BeanReaderErrorHandler beanReaderErrorHandler;
    private String beanReaderErrorHandlerType;
    private boolean unmarshalSingleObject;

    public String getMapping() {
        return mapping;
    }

    public void setMapping(String mapping) {
        this.mapping = mapping;
    }

    public String getStreamName() {
        return streamName;
    }

    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public boolean isIgnoreUnidentifiedRecords() {
        return ignoreUnidentifiedRecords;
    }

    public void setIgnoreUnidentifiedRecords(boolean ignoreUnidentifiedRecords) {
        this.ignoreUnidentifiedRecords = ignoreUnidentifiedRecords;
    }

    public boolean isIgnoreUnexpectedRecords() {
        return ignoreUnexpectedRecords;
    }

    public void setIgnoreUnexpectedRecords(boolean ignoreUnexpectedRecords) {
        this.ignoreUnexpectedRecords = ignoreUnexpectedRecords;
    }

    public boolean isIgnoreInvalidRecords() {
        return ignoreInvalidRecords;
    }

    public void setIgnoreInvalidRecords(boolean ignoreInvalidRecords) {
        this.ignoreInvalidRecords = ignoreInvalidRecords;
    }

    public Charset getEncoding() {
        return encoding;
    }

    public void setEncoding(Charset encoding) {
        this.encoding = encoding;
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public BeanReaderErrorHandler getBeanReaderErrorHandler() {
        return beanReaderErrorHandler;
    }

    public void setBeanReaderErrorHandler(BeanReaderErrorHandler beanReaderErrorHandler) {
        this.beanReaderErrorHandler = beanReaderErrorHandler;
    }

    public String getBeanReaderErrorHandlerType() {
        return beanReaderErrorHandlerType;
    }

    public void setBeanReaderErrorHandlerType(String beanReaderErrorHandlerType) {
        this.beanReaderErrorHandlerType = beanReaderErrorHandlerType;
    }

    public void setBeanReaderErrorHandlerType(Class<?> beanReaderErrorHandlerType) {
        this.beanReaderErrorHandlerType = beanReaderErrorHandlerType.getName();
    }

    public boolean isUnmarshalSingleObject() {
        return unmarshalSingleObject;
    }

    public void setUnmarshalSingleObject(boolean unmarshalSingleObject) {
        this.unmarshalSingleObject = unmarshalSingleObject;
    }
}
