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

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.model.ToDefinition;

@XmlRootElement(name = "path")
@XmlAccessorType(XmlAccessType.FIELD)
public class PathDefinition {

    @XmlAttribute(required = true)
    private String uri;
    @XmlElementRef
    private List<VerbDefinition> verbs = new ArrayList<VerbDefinition>();
    @XmlTransient
    private RestDefinition rest;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public List<VerbDefinition> getVerbs() {
        return verbs;
    }

    public void setVerbs(List<VerbDefinition> verbs) {
        this.verbs = verbs;
    }

     public RestDefinition getRest() {
        return rest;
    }

    public void setRest(RestDefinition rest) {
        this.rest = rest;
    }

    // Fluent API
    //-------------------------------------------------------------------------

    public PathDefinition path(String uri) {
        // add a new path on the rest
        return rest.path(uri);
    }

    public PathDefinition get() {
        return addVerb("get", null);
    }

    public PathDefinition post() {
        return addVerb("post", null);
    }

    public PathDefinition put() {
        return addVerb("put", null);
    }

    public PathDefinition delete() {
        return addVerb("delete", null);
    }

    public PathDefinition head() {
        return addVerb("head", null);
    }

    public PathDefinition verb(String verb) {
        return addVerb(verb, null);
    }

    public PathDefinition consumes(String accept) {
        // add to last verb
        if (getVerbs().isEmpty()) {
            throw new IllegalArgumentException("Must add verb first, such as get/post/delete");
        }

        VerbDefinition verb = getVerbs().get(getVerbs().size() - 1);
        verb.setConsumes(accept);

        return this;
    }

    @Deprecated
    public VerbDefinition to(String url) {
        // add to last verb
        if (getVerbs().isEmpty()) {
            throw new IllegalArgumentException("Must add verb first, such as get/post/delete");
        }

        VerbDefinition verb = getVerbs().get(getVerbs().size() - 1);
        verb.addOutput(new ToDefinition(url));

        return verb;
    }

    private PathDefinition addVerb(String verb, String url) {
        VerbDefinition answer;

        if ("get".equals(verb)) {
            answer = new GetVerbDefinition();
        } else if ("post".equals(verb)) {
            answer = new PostVerbDefinition();
        } else if ("delete".equals(verb)) {
            answer = new DeleteVerbDefinition();
        } else if ("head".equals(verb)) {
            answer = new HeadVerbDefinition();
        } else if ("put".equals(verb)) {
            answer = new PutVerbDefinition();
        } else {
            answer = new VerbDefinition();
            answer.setMethod(verb);
        }

        answer.setPath(this);
        getVerbs().add(answer);
        return this;
    }

}
