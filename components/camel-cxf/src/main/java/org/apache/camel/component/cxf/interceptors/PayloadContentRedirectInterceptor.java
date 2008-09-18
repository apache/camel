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
package org.apache.camel.component.cxf.interceptors;

import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.w3c.dom.Element;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.staxutils.StaxUtils;

public class PayloadContentRedirectInterceptor extends AbstractPhaseInterceptor<Message> {

    public PayloadContentRedirectInterceptor() {
        super(Phase.POST_STREAM);
    }

    @SuppressWarnings("unchecked")
    public void handleMessage(Message message) throws Fault {
        // check the fault from the message
        Throwable ex = message.getContent(Throwable.class);
        if (ex != null) {
            if (ex instanceof Fault) {
                throw (Fault)ex;
            } else {
                throw new Fault(ex);
            }
        }

        XMLStreamWriter out = message.getContent(XMLStreamWriter.class);
        List<Element> in = message.get(List.class);
        try {
            for (Element el : in) {
                StaxUtils.writeElement(el, out, false, true);
            }
        } catch (XMLStreamException e) {
            throw new Fault(e);
        }
    }
}
