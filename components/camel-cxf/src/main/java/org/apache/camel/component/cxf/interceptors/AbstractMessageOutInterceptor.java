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
//import java.util.ResourceBundle;
//import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

//import org.apache.cxf.common.i18n.BundleUtils;
import org.apache.cxf.helpers.DOMUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;

/**
 * This is the base class for message interceptors that intercepts
 * binding specific headers and message parts as DOM Element.
 * Then moves dom elements between header and message part list based on the
 * binding operation info provided in the exchange.
 */
public abstract class AbstractMessageOutInterceptor<T extends Message>
       extends AbstractPhaseInterceptor<T> {
    
    
    public AbstractMessageOutInterceptor(String phase) {
        super(phase);
    }
    protected boolean isRequestor(Message message) {
        return Boolean.TRUE.equals(message.get(Message.REQUESTOR_ROLE));
    }

    protected abstract Logger getLogger();
    
    protected Element createElement(QName elName, List<Element> childEl) {
        Document doc = DOMUtils.createDocument();

        String prefix = elName.getPrefix();
        StringBuilder tagName = new StringBuilder();
        if (!"".equals(prefix)) {
            tagName.append(prefix);
            tagName.append(":");
        }
        tagName.append(elName.getLocalPart());
        
        Element el = doc.createElementNS(elName.getNamespaceURI(),
                                         tagName.toString());

        if (!"".equals(elName.getPrefix())) {
            StringBuilder attrName = new StringBuilder("xmlns");        
            attrName.append(':');
            attrName.append(elName.getPrefix());
            el.setAttribute(attrName.toString(), elName.getNamespaceURI());
        }

        for (Element part : childEl) {
            Node adoptedNode = doc.adoptNode(part);
            el.appendChild(adoptedNode);
        }

        return el;
    }    
}
