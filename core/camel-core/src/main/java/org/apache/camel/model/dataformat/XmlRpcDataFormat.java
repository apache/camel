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
 * The XML RPC data format is used for working with the XML RPC protocol.
 */
@Metadata(firstVersion = "2.11.0", label = "dataformat,transformation,xml", title = "XML RPC")
@XmlRootElement(name = "xmlrpc")
@XmlAccessorType(XmlAccessType.FIELD)
public class XmlRpcDataFormat extends DataFormatDefinition {
    @XmlAttribute
    private Boolean request;
    
    public XmlRpcDataFormat() {
        super("xmlrpc");
    }
    
    @Override
    protected void configureDataFormat(DataFormat dataFormat, CamelContext camelContext) {
        if (request != null) {
            setProperty(camelContext, dataFormat, "request", request);
        }
    }

    public Boolean getRequest() {
        return request;
    }

    /**
     * Whether to marshal/unmarshal request or response
     * <p/>
     * Is by default false
     */
    public void setRequest(Boolean request) {
        this.request = request;
    }

}
