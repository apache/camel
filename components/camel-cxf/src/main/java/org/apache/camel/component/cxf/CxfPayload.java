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
package org.apache.camel.component.cxf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Element;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.cxf.staxutils.StaxUtils;


/**
 * CxfMessage body type when {@link DataFormat#PAYLOAD} is used.
 * 
 * @version 
 */
public class CxfPayload<T> {
    
    private List<Source> body;
    private List<T> headers;
    private Map<String, String> nsMap;
    
    public CxfPayload(List<T> headers, List<Source> body, Map<String, String> nsMap) {
        this.headers = headers;
        this.body = body;
        this.nsMap = nsMap;
    }
    public CxfPayload(List<T> headers, List<Element> body) {
        this.headers = headers;
        this.body = new ArrayList<Source>(body.size());
        for (Element el : body) {
            this.body.add(new DOMSource(el));
        }
    } 
    
    /**
     * Get the body as a List of DOM elements. 
     * This will cause the Body to be fully read and parsed.
     * @return
     */
    public List<Element> getBody() {
        List<Element> els = new ArrayList<Element>();
        for (int x = 0; x < body.size(); x++) {
            Source s = body.get(x);
            try {
                Element el = StaxUtils.read(s).getDocumentElement();
                addNamespace(el, nsMap);
                els.add(el);
                body.set(x, new DOMSource(el));
            } catch (Exception ex) {
                throw new RuntimeCamelException("Problem converting content to Element", ex);
            }
        }
        return els;
    }
    
    protected static void addNamespace(Element element, Map<String, String> nsMap) {
        if (nsMap != null) {
            for (String ns : nsMap.keySet()) {
                element.setAttribute(XMLConstants.XMLNS_ATTRIBUTE + ":" + ns, nsMap.get(ns));
            }
        }
    }

    /**
     * Gets the body as a List of source objects
     * @return
     */
    public List<Source> getBodySources() {
        return body;
    }
    
    public List<T> getHeaders() {
        return headers;
    }
    
    public String toString() {
        XmlConverter converter = new XmlConverter();
        StringBuilder buf = new StringBuilder();
        buf.append(getClass().getName());
        buf.append(" headers: " + headers);
        // go through the list of element and turn it into String
        if (body == null) {
            buf.append("body: " + body);
        } else {
            buf.append("body: [ ");
            for (Element src : getBody()) {
                String elementString = "";
                try {
                    elementString = converter.toString(src, null);
                } catch (TransformerException e) {
                    elementString = src.toString();
                }
                buf.append("[" + elementString + "]");
            }
            buf.append("]");
        }
        return buf.toString();
    }

}
