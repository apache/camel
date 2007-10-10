/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.builder;

import org.apache.camel.model.ProcessorType;
import org.apache.camel.model.dataformat.ArtixDSContentType;
import org.apache.camel.model.dataformat.ArtixDSDataFormat;
import org.apache.camel.model.dataformat.DataFormatType;
import org.apache.camel.model.dataformat.JaxbDataFormat;
import org.apache.camel.model.dataformat.SerializationDataFormat;
import org.apache.camel.model.dataformat.XMLBeansDataFormat;
import org.apache.camel.spi.DataFormat;

/**
 * An expression for constructing the different possible {@link DataFormat}
 * options.
 *
 * @version $Revision: 1.1 $
 */
public class DataTypeExpression<T extends ProcessorType> {
    private final ProcessorType<T> processorType;
    private final Operation operation;

    public enum Operation {
        Marshal, Unmarshal
    };

    public DataTypeExpression(ProcessorType<T> processorType, Operation operation) {
        this.processorType = processorType;
        this.operation = operation;
    }

    /**
     * Uses the Java Serialization data format
     */
    public T serialization() {
        return dataFormat(new SerializationDataFormat());
    }

    /**
     * Uses the JAXB data format
     */
    public T jaxb() {
        return dataFormat(new JaxbDataFormat());
    }

    /**
     * Uses the JAXB data format turning pretty printing on or off
     */
    public T jaxb(boolean prettyPrint) {
        return dataFormat(new JaxbDataFormat(prettyPrint));
    }

    /**
     * Uses the JAXB data format
     */
    public T xmlBeans() {
        return dataFormat(new XMLBeansDataFormat());
    }

    /**
     * Uses the
     * <a href="http://activemq.apache.org/camel/artix-data-services.html">Artix Data Services</a>
     * data format for dealing with lots of different message formats such as SWIFT etc.
     */
    public T artixDS() {
        return dataFormat(new ArtixDSDataFormat());
    }

    /**
     * Uses the
     * <a href="http://activemq.apache.org/camel/artix-data-services.html">Artix Data Services</a>
     * data format with the specified type of ComplexDataObject
     * for marshalling and unmarshalling messages using the dataObject's default Source and Sink.
     */
    public T artixDS(Class<?> dataObjectType) {
        return dataFormat(new ArtixDSDataFormat(dataObjectType));
    }


    /**
     * Uses the
     * <a href="http://activemq.apache.org/camel/artix-data-services.html">Artix Data Services</a>
     * data format with the specified type of ComplexDataObject
     * for marshalling and unmarshalling messages using the dataObject's default Source and Sink.
     */
    public T artixDS(Class<?> elementType, ArtixDSContentType contentType) {
        return dataFormat(new ArtixDSDataFormat(elementType, contentType));
    }

    /**
     * Uses the
     * <a href="http://activemq.apache.org/camel/artix-data-services.html">Artix Data Services</a>
     * data format with the specified content type
     * for marshalling and unmarshalling messages
     */
    public T artixDS(ArtixDSContentType contentType) {
        return dataFormat(new ArtixDSDataFormat(contentType));
    }

    private T dataFormat(DataFormatType dataFormatType) {
        switch (operation) {
            case Unmarshal:
                return processorType.unmarshal(dataFormatType);
            case Marshal:
                return processorType.marshal(dataFormatType);
            default:
                throw new IllegalArgumentException("Unknown value: " + operation);
        }
    }

}
