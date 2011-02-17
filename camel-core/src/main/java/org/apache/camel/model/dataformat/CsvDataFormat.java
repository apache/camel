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
 * Represents a CSV (Comma Separated Values) {@link org.apache.camel.spi.DataFormat}
 *
 * @version 
 */
@XmlRootElement(name = "csv")
@XmlAccessorType(XmlAccessType.FIELD)
public class CsvDataFormat extends DataFormatDefinition {
    @XmlAttribute(required = false)
    private String delimiter;

    public CsvDataFormat() {
        super("csv");
    }
    
    public CsvDataFormat(String delimiter) {
        this();
        setDelimiter(delimiter);
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }
    
    @Override
    protected void configureDataFormat(DataFormat dataFormat) {
        if (delimiter != null) {
            if (delimiter.length() > 1) {
                throw new IllegalArgumentException("Delimiter must have a length of one!");
            }
            setProperty(dataFormat, "delimiter", delimiter);
        } else { // the default delimiter is ','
            setProperty(dataFormat, "delimiter", ",");
        }
    }
}