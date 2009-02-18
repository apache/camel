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
package org.apache.camel.rest.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Endpoint;

/**
 * @version $Revision$
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class EndpointLink {
    @XmlAttribute
    private String uri;
    @XmlAttribute
    private String href;

    public EndpointLink() {
    }

    public EndpointLink(Endpoint endpoint) {
        this();
        load(endpoint);
    }

    @Override
    public String toString() {
        return "EndpointLink{href='" + href + "' uri='" + uri + "'}";
    }

    public void load(Endpoint endpoint) {
        this.uri = endpoint.getEndpointUri();
        this.href = createHref(uri);
    }

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    protected String createHref(String uri) {
        // TODO how to encode as a href?
        return "/endpoints/" + uri;
    }
}
