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

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Element;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.staxutils.StaxUtils;

/**
 * CxfMessage body type when {@link DataFormat#PAYLOAD} is used.
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
        this.body = new ArrayList<>(body.size());
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
        return new AbstractList<Element>() {
            public boolean add(Element e) {
                return body.add(new DOMSource(e));
            }

            public Element set(int index, Element element) {
                Source s = body.set(index, new DOMSource(element));
                try {
                    return StaxUtils.read(s).getDocumentElement();
                } catch (XMLStreamException e) {
                    throw new RuntimeCamelException("Problem converting content to Element", e);
                }
            }

            public void add(int index, Element element) {
                body.add(index, new DOMSource(element));
            }

            public Element remove(int index) {
                Source s = body.remove(index);
                try {
                    return StaxUtils.read(s).getDocumentElement();
                } catch (XMLStreamException e) {
                    throw new RuntimeCamelException("Problem converting content to Element", e);
                }
            }

            public Element get(int index) {
                Source s = body.get(index);
                try {
                    Element el = StaxUtils.read(s).getDocumentElement();
                    addNamespace(el, nsMap);
                    body.set(index, new DOMSource(el));
                    return el;
                } catch (Exception ex) {
                    throw new RuntimeCamelException("Problem converting content to Element", ex);
                }
            }

            public int size() {
                return body.size();
            }
        };
    }
    
    protected static void addNamespace(Element element, Map<String, String> nsMap) {
        if (nsMap != null) {
            for (String ns : nsMap.keySet()) {
                // We should not override the namespace setting of the element
                if (XMLConstants.XMLNS_ATTRIBUTE.equals(ns)) {
                    if (ObjectHelper.isEmpty(element.getAttribute(XMLConstants.XMLNS_ATTRIBUTE))) {
                        element.setAttribute(ns, nsMap.get(ns));
                    }
                } else {
                    if (ObjectHelper.isEmpty(element.getAttribute(XMLConstants.XMLNS_ATTRIBUTE + ":" + ns))) {
                        element.setAttribute(XMLConstants.XMLNS_ATTRIBUTE + ":" + ns, nsMap.get(ns));
                    }
                }
            }
        }
    }

    /**
     * Gets the body as a List of source objects.   If possible, the Source objects
     * will likely be StaxSource or similar that allows streaming.   If you plan to 
     * modify or use the Source, be careful that the result is still usable by
     * the Camel runtime.
     * @return
     */
    public List<Source> getBodySources() {
        return body;
    }
    
    /*
     * set the body back with cached stream source so
     * CxfPayload is re-readable
     */
    public void setBodySources(List<Source> body) {
        this.body = body;
    }
    
    public List<T> getHeaders() {
        return headers;
    }

    public Map<String, String> getNsMap() {
        return nsMap;
    }

    @Override
    public String toString() {
        // do not load or print the payload body etc as we do not want to load that into memory etc
        return super.toString();
    }

}
