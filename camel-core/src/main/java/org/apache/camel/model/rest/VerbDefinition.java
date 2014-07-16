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
package org.apache.camel.model.rest;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.model.OutputDefinition;

@XmlRootElement(name = "verb")
@XmlAccessorType(XmlAccessType.FIELD)
public class VerbDefinition extends OutputDefinition<VerbDefinition> {

    @XmlAttribute
    private String method;

    @XmlAttribute
    private String accept;

    @XmlTransient
    private PathDefinition path;

    public PathDefinition getPath() {
        return path;
    }

    public void setPath(PathDefinition path) {
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getAccept() {
        return accept;
    }

    public void setAccept(String accept) {
        this.accept = accept;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    public PathDefinition path(String uri) {
        PathDefinition parent = endPath();
        return parent.path(uri);
    }

    public PathDefinition get() {
        return path.get();
    }

    public PathDefinition post() {
        return path.post();
    }

    public PathDefinition put() {
        return path.put();
    }

    public PathDefinition delete() {
        return path.delete();
    }

    public PathDefinition head() {
        return path.head();
    }

    public PathDefinition verb(String verb) {
        return path.verb(verb);
    }

}
