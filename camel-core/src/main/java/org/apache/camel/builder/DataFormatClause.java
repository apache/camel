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
package org.apache.camel.builder;

import java.util.zip.Deflater;

import org.w3c.dom.Node;

import org.apache.camel.model.ProcessorType;
import org.apache.camel.model.dataformat.ArtixDSContentType;
import org.apache.camel.model.dataformat.ArtixDSDataFormat;
import org.apache.camel.model.dataformat.CsvDataFormat;
import org.apache.camel.model.dataformat.DataFormatType;
import org.apache.camel.model.dataformat.HL7DataFormat;
import org.apache.camel.model.dataformat.JaxbDataFormat;
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.apache.camel.model.dataformat.SerializationDataFormat;
import org.apache.camel.model.dataformat.StringDataFormat;
import org.apache.camel.model.dataformat.TidyMarkupDataFormat;
import org.apache.camel.model.dataformat.XMLBeansDataFormat;
import org.apache.camel.model.dataformat.XStreamDataFormat;
import org.apache.camel.model.dataformat.ZipDataFormat;
import org.apache.camel.spi.DataFormat;

/**
 * An expression for constructing the different possible {@link DataFormat}
 * options.
 *
 * @version $Revision$
 */
public class DataFormatClause<T extends ProcessorType> {
    private final T processorType;
    private final Operation operation;

    /**
     * {@link DataFormat} operations.
     */
    public enum Operation {
        Marshal, Unmarshal
    }

    public DataFormatClause(T processorType, Operation operation) {
        this.processorType = processorType;
        this.operation = operation;
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

    /**
     * Uses the CSV data format
     */
    public T csv() {
        return dataFormat(new CsvDataFormat());
    }

    /**
     * Uses the HL7 data format
     */
    public T hl7() {
        return dataFormat(new HL7DataFormat());
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
     * Uses the Java Serialization data format
     */
    public T serialization() {
        return dataFormat(new SerializationDataFormat());
    }

    /**
     * Uses the String data format
     */
    public T string() {
        return string(null);
    }

    /**
     * Uses the String data format supporting encoding using given charset
     */
    public T string(String charset) {
        StringDataFormat sdf = new StringDataFormat();
        sdf.setCharset(charset);
        return dataFormat(sdf);
    }

    /**
     * Uses the JAXB data format
     */
    public T xmlBeans() {
        return dataFormat(new XMLBeansDataFormat());
    }

    /**
     * Return WellFormed HTML (an XML Document) either 
     * {@link java.lang.String} or {@link org.w3c.dom.Node}
     */
    public T tidyMarkup(Class<?> dataObjectType) {
        return dataFormat(new TidyMarkupDataFormat(dataObjectType));
    }

    /**
     * Return TidyMarkup in the default format 
     *  as {@link org.w3c.dom.Node}
     */
    public T tidyMarkup() {
        return dataFormat(new TidyMarkupDataFormat(Node.class));
    }

    
    /**
     * Uses the XStream data format
     */
    public T xstream() {
        return dataFormat(new XStreamDataFormat());
    }
    
    /**
     * Uses the JSON data format
     */
    public T json() {
        return dataFormat(new JsonDataFormat());
    }

    private T dataFormat(DataFormatType dataFormatType) {
        switch (operation) {
        case Unmarshal:
            return (T)processorType.unmarshal(dataFormatType);
        case Marshal:
            return (T)processorType.marshal(dataFormatType);
        default:
            throw new IllegalArgumentException("Unknown DataFormat operation: " + operation);
        }
    }

    public T zip() {
        ZipDataFormat zdf = new ZipDataFormat(Deflater.DEFAULT_COMPRESSION);
        return dataFormat(zdf);
    }

    public T zip(int compressionLevel) {
        ZipDataFormat zdf = new ZipDataFormat(compressionLevel);
        return dataFormat(zdf);
    }
}
