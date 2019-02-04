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
 * The Zip File data format is a message compression and de-compression format of zip files.
 */
@Metadata(firstVersion = "2.11.0", label = "dataformat,transformation,file", title = "Zip File")
// TODO: use zipfile as name in Camel 3.0
@XmlRootElement(name = "zipFile")
@XmlAccessorType(XmlAccessType.FIELD)
public class ZipFileDataFormat extends DataFormatDefinition {
    @XmlAttribute
    private Boolean usingIterator;
    @XmlAttribute
    private Boolean allowEmptyDirectory;
    @XmlAttribute
    private Boolean preservePathElements;

    public ZipFileDataFormat() {
        super("zipfile");
    }
    
    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (usingIterator != null) {
            setProperty(camelContext, dataFormat, "usingIterator", usingIterator);
        }
        if (allowEmptyDirectory != null) {
            setProperty(camelContext, dataFormat, "allowEmptyDirectory", allowEmptyDirectory);
        }
        if (preservePathElements != null) {
            setProperty(camelContext, dataFormat, "preservePathElements", preservePathElements);
        }
    }

    public Boolean getUsingIterator() {
        return usingIterator;
    }
    
    public Boolean getAllowEmptyDirectory() {
        return allowEmptyDirectory;
    }

    public Boolean getPreservePathElements() {
        return preservePathElements;
    }

    /**
     * If the zip file has more then one entry, the setting this option to true, allows to work with the splitter EIP,
     * to split the data using an iterator in a streaming mode.
     */
    public void setUsingIterator(Boolean usingIterator) {
        this.usingIterator = usingIterator;
    }
    
    /**
     * If the zip file has more then one entry, setting this option to true, allows to get the iterator
     * even if the directory is empty
     */
    public void setAllowEmptyDirectory(Boolean allowEmptyDirectory) {
        this.allowEmptyDirectory = allowEmptyDirectory;
    }

    /**
     * If the file name contains path elements, setting this option to true, allows the path to be maintained
     * in the zip file.
     */
    public void setPreservePathElements(Boolean preservePathElements) {
        this.preservePathElements = preservePathElements;
    }

}
