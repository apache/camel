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
package org.apache.camel.component.cxf;

import java.util.logging.Logger;

import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.w3c.dom.Node;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.databinding.DataReader;
import org.apache.cxf.databinding.DataWriter;
import org.apache.cxf.databinding.source.NodeDataReader;
import org.apache.cxf.databinding.source.NodeDataWriter;
import org.apache.cxf.databinding.source.SourceDataBinding;
import org.apache.cxf.databinding.source.XMLStreamDataReader;
import org.apache.cxf.databinding.source.XMLStreamDataWriter;
import org.apache.cxf.jaxb.JAXBDataBinding;
import org.apache.cxf.service.model.MessagePartInfo;

/**
 * This is a hybrid DataBinding of {@link JAXBDataBinding} and {@link org.apache.cxf.databinding.source.SourceDataBinding}.
 * Like the SourceDataBinding, this DataBinding de/serializes parameters as DOMSource objects.  And like the JAXBDataBinding, the 
 * {@link #initialize(org.apache.cxf.service.Service)}
 * method can initialize the service model's message part schema based on the service class in the message part info.  
 * Hence, this DataBinding supports DOMSource object de/serialization without requiring users to provide a WSDL.
 */
public class HybridSourceDataBinding extends JAXBDataBinding {
    private static final Logger LOG = LogUtils.getL7dLogger(SourceDataBinding.class);
    
    public HybridSourceDataBinding() {
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <T> DataReader<T> createReader(Class<T> cls) {
        if (cls == XMLStreamReader.class) {
            return (DataReader<T>) new XMLStreamDataReader();
        } else if (cls == Node.class) {
            return (DataReader<T>) new NodeDataReader();
        } else {
            throw new UnsupportedOperationException("The type " + cls.getName() + " is not supported.");
        }
    }

    @Override
    public Class<?>[] getSupportedReaderFormats() {
        return new Class[] {XMLStreamReader.class, Node.class};
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <T> DataWriter<T> createWriter(Class<T> cls) {
        if (cls == XMLStreamWriter.class) {
            return (DataWriter<T>) new XMLStreamDataWriter() {

                public void write(Object obj, MessagePartInfo part, XMLStreamWriter output) {
                    if (obj == null) {
                        return;
                    }
                    super.write(obj, part, output);
                }
                
            };
        } else if (cls == Node.class) {
            return (DataWriter<T>) new NodeDataWriter();
        } else {
            throw new UnsupportedOperationException("The type " + cls.getName() + " is not supported.");
        }
    }
    
    @Override
    public Class<?>[] getSupportedWriterFormats() {
        return new Class[] {XMLStreamWriter.class, Node.class};
    }

}
