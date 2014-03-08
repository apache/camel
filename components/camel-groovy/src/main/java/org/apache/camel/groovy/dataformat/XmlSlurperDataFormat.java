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
package org.apache.camel.groovy.dataformat;

import java.io.InputStream;
import java.io.OutputStream;

import groovy.util.XmlSlurper;
import org.apache.camel.Exchange;

/**
 * DataFormat for using groovy.util.XmlSlurper as parser for XML data
 */
public class XmlSlurperDataFormat extends AbstractXmlDataFormat {

    public XmlSlurperDataFormat() {
        this(true);
    }

    public XmlSlurperDataFormat(boolean namespaceAware) {
        super(namespaceAware);
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        throw new UnsupportedOperationException("XmlSlurper does not support marshalling");
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        return newSlurper().parse(stream);
    }

    private XmlSlurper newSlurper() throws Exception {
        XmlSlurper slurper = new XmlSlurper(newSaxParser());
        slurper.setErrorHandler(getErrorHandler());
        slurper.setKeepWhitespace(isKeepWhitespace());
        return slurper;
    }

}
