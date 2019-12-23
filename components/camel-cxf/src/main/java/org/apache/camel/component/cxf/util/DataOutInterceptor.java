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
package org.apache.camel.component.cxf.util;

import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.interceptor.AbstractOutDatabindingInterceptor;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.staxutils.StaxUtils;

public class DataOutInterceptor extends AbstractOutDatabindingInterceptor {
    // CXF requires java.util.logging for Message
    private static final Logger JUL_LOG = LogUtils.getL7dLogger(DataOutInterceptor.class);

    public DataOutInterceptor() {
        super(Phase.MARSHAL);
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        XMLStreamWriter xmlWriter = getXMLStreamWriter(message);
        try {
            // put the payload source as a document
            Source source = message.getContent(Source.class);
            if (source != null) {
                XMLStreamReader xmlReader = StaxUtils.createXMLStreamReader(source);
                StaxUtils.copy(xmlReader, xmlWriter);
            }
        } catch (XMLStreamException e) {
            throw new Fault(new org.apache.cxf.common.i18n.Message("XMLSTREAM_EXCEPTION", JUL_LOG, e), e);
        }
    }

}
