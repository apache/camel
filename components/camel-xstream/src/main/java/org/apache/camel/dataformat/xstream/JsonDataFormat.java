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

package org.apache.camel.dataformat.xstream;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

import javax.xml.stream.XMLStreamException;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.QNameMap;
import com.thoughtworks.xstream.io.xml.StaxReader;
import com.thoughtworks.xstream.io.xml.StaxWriter;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.codehaus.jettison.mapped.MappedXMLInputFactory;
import org.codehaus.jettison.mapped.MappedXMLOutputFactory;

/**
 * A <a href="http://activemq.apache.org/camel/data-format.html">data format</a>
 * ({@link DataFormat}) using XStream and Jettison to marshal to and from JSON
 *
 * @version $Revision$
 */

public class JsonDataFormat extends AbstractXStreamWrapper {
    private final MappedXMLOutputFactory mof;
    private final MappedXMLInputFactory mif;
    
    public JsonDataFormat() {
        final HashMap nstjsons = new HashMap();
        mof = new MappedXMLOutputFactory(nstjsons);
        mif = new MappedXMLInputFactory(nstjsons);
    }
    
    
    
    protected HierarchicalStreamWriter createHierarchicalStreamWriter(Exchange exchange, Object body, OutputStream stream) throws XMLStreamException {        
        return new StaxWriter(new QNameMap(), mof.createXMLStreamWriter(stream));
    }

    protected HierarchicalStreamReader createHierarchicalStreamReader(Exchange exchange, InputStream stream) throws XMLStreamException {        
        return new StaxReader(new QNameMap(), mif.createXMLStreamReader(stream));
    }

}
