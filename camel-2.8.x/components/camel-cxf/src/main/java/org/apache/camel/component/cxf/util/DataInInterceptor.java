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
package org.apache.camel.component.cxf.util;

import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import org.w3c.dom.Document;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.interceptor.AbstractInDatabindingInterceptor;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.staxutils.DepthXMLStreamReader;
import org.apache.cxf.staxutils.StaxUtils;


public class DataInInterceptor extends AbstractInDatabindingInterceptor {
    // CXF requires JUL for Message
    private static final Logger JUL_LOG = LogUtils.getL7dLogger(DataInInterceptor.class);

    public DataInInterceptor() {
        super(Phase.UNMARSHAL);
    }

    public void handleMessage(Message message) throws Fault {
        DepthXMLStreamReader xmlReader = getXMLStreamReader(message);
        try {
            // put the payload source as a document
            Document doc = StaxUtils.read(xmlReader);
            message.setContent(Source.class, new DOMSource(doc));
        } catch (XMLStreamException e) {
            throw new Fault(new org.apache.cxf.common.i18n.Message("XMLSTREAM_EXCEPTION", JUL_LOG), e);
        }
    }

}
