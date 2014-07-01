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
package org.apache.camel.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.RouteContext;

@XmlRootElement(name = "fromRest")
@XmlAccessorType(XmlAccessType.FIELD)
public class FromRestDefinition extends FromDefinition {

    // TODO: verb should be an enum

    @XmlAttribute
    private String verb;
    @XmlAttribute
    private String path;
    @XmlAttribute
    private String accept;
    @XmlTransient
    private Endpoint endpoint;

    public FromRestDefinition() {
    }

    public FromRestDefinition(String verb, String path) {
        this(verb, path, null);
    }

    public FromRestDefinition(String verb, String path, String accept) {
        this.verb = verb;
        this.path = path;
        this.accept = accept;
    }

    public Endpoint resolveEndpoint(RouteContext context) {
        if (endpoint == null) {
            if (accept != null) {
                return context.resolveEndpoint("rest-binding:" + verb + ":" + path + "?accept=" + accept);
            } else {
                return context.resolveEndpoint("rest-binding:" + verb + ":" + path);
            }
        } else {
            return endpoint;
        }
    }

    @Override
    public String toString() {
        return "fromRest[" + getLabel() + "]";
    }

    @Override
    public String getShortName() {
        return "fromRest";
    }

    public String getLabel() {
        return verb + "(" + "/" + path + ")";
    }

    public String getVerb() {
        return verb;
    }

    public String getPath() {
        return path;
    }

    public String getAccept() {
        return accept;
    }
}
