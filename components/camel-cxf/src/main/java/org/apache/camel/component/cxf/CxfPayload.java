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

import java.util.List;

import javax.xml.transform.TransformerException;

import org.w3c.dom.Element;

import org.apache.camel.converter.jaxp.XmlConverter;

/**
 * CxfMessage body type when {@link DataFormat#PAYLOAD} is used.
 * 
 * @version $Revision$
 */
public class CxfPayload<T> {
    
    private List<Element> body;
    private List<T> headers;

    public CxfPayload(List<T> headers, List<Element> body) {
        this.headers = headers;
        this.body = body;
    }
    
    public List<Element> getBody() {
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
            for (Element element : body) {
                String elementString = "";
                try {
                    elementString = converter.toString(element, null);
                } catch (TransformerException e) {
                    elementString = element.toString();
                }
                buf.append("[" + elementString + "]");
            }
            buf.append("]");
        }
        return buf.toString();
    }

}
