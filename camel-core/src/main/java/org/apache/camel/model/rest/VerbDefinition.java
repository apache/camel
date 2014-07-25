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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.ToDefinition;

@XmlRootElement(name = "verb")
@XmlAccessorType(XmlAccessType.FIELD)
public class VerbDefinition {

    @XmlAttribute
    private String method;

    @XmlAttribute
    private String uri;

    @XmlAttribute
    private String consumes;

    @XmlAttribute
    private String produces;

    @XmlAttribute
    private RestBindingMode bindingMode;

    @XmlAttribute
    private String type;

    @XmlAttribute
    private String typeList;

    @XmlAttribute
    private String outType;

    @XmlAttribute
    private String outTypeList;

    @XmlTransient
    private Class<?> classType;

    @XmlTransient
    private Class<?> outClassType;

    @XmlTransient
    private boolean useList;

    @XmlTransient
    private boolean outUseList;

    // used by XML DSL to either select a <to> or <route>
    // so we need to use the common type OptionalIdentifiedDefinition
    @XmlElements({
            @XmlElement(required = false, name = "to", type = ToDefinition.class),
            @XmlElement(required = false, name = "route", type = RouteDefinition.class)}
    )
    private OptionalIdentifiedDefinition toOrRoute;

    // the Java DSL uses the to or route definition directory
    @XmlTransient
    private ToDefinition to;
    @XmlTransient
    private RouteDefinition route;
    @XmlTransient
    private RestDefinition rest;

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getConsumes() {
        return consumes;
    }

    public void setConsumes(String consumes) {
        this.consumes = consumes;
    }

    public String getProduces() {
        return produces;
    }

    public void setProduces(String produces) {
        this.produces = produces;
    }

    public RestBindingMode getBindingMode() {
        return bindingMode;
    }

    public void setBindingMode(RestBindingMode bindingMode) {
        this.bindingMode = bindingMode;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
        this.useList = false;
    }

    public String getTypeList() {
        return typeList;
    }

    public void setTypeList(String typeList) {
        this.typeList = typeList;
        this.useList = true;
    }

    public Class<?> getClassType() {
        return classType;
    }

    public void setClassType(Class<?> classType) {
        this.classType = classType;
        this.useList = false;
    }

    public void setListClassType(Class<?> classType) {
        this.classType = classType;
        this.useList = true;
    }

    public boolean isUseList() {
        return useList;
    }

    public String getOutType() {
        return outType;
    }

    public void setOutType(String type) {
        this.outType = type;
        this.outUseList = false;
    }

    public String getOutTypeList() {
        return outTypeList;
    }

    public void setOutTypeList(String typeList) {
        this.outTypeList = typeList;
        this.outUseList = true;
    }

    public Class<?> getOutClassType() {
        return outClassType;
    }

    public void setOutClassType(Class<?> classType) {
        this.outClassType = classType;
        this.outUseList = false;
    }

    public void setOutListClassType(Class<?> classType) {
        this.outClassType = classType;
        this.outUseList = true;
    }

    public boolean isOutUseList() {
        return outUseList;
    }

    public RestDefinition getRest() {
        return rest;
    }

    public void setRest(RestDefinition rest) {
        this.rest = rest;
    }

    public RouteDefinition getRoute() {
        if (route != null) {
            return route;
        } else if (toOrRoute instanceof RouteDefinition) {
            return (RouteDefinition) toOrRoute;
        } else {
            return null;
        }
    }

    public void setRoute(RouteDefinition route) {
        this.route = route;
        this.toOrRoute = route;
    }

    public ToDefinition getTo() {
        if (to != null) {
            return to;
        } else if (toOrRoute instanceof ToDefinition) {
            return (ToDefinition) toOrRoute;
        } else {
            return null;
        }
    }

    public void setTo(ToDefinition to) {
        this.to = to;
        this.toOrRoute = to;
    }

    public OptionalIdentifiedDefinition getToOrRoute() {
        return toOrRoute;
    }

    public void setToOrRoute(OptionalIdentifiedDefinition toOrRoute) {
        this.toOrRoute = toOrRoute;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    public RestDefinition get() {
        return rest.get();
    }

    public RestDefinition get(String uri) {
        return rest.get(uri);
    }

    public RestDefinition post() {
        return rest.post();
    }

    public RestDefinition post(String uri) {
        return rest.post(uri);
    }

    public RestDefinition put() {
        return rest.put();
    }

    public RestDefinition put(String uri) {
        return rest.put(uri);
    }

    public RestDefinition delete() {
        return rest.delete();
    }

    public RestDefinition delete(String uri) {
        return rest.delete(uri);
    }

    public RestDefinition head() {
        return rest.head();
    }

    public RestDefinition head(String uri) {
        return rest.head(uri);
    }

    public RestDefinition verb(String verb) {
        return rest.verb(verb);
    }

    public RestDefinition verb(String verb, String uri) {
        return rest.verb(verb, uri);
    }

    public String asVerb() {
        // we do not want the jaxb model to repeat itself, by outputting <get method="get">
        // so we defer the verb from the instance type
        if (this instanceof GetVerbDefinition) {
            return "get";
        } else if (this instanceof PostVerbDefinition) {
            return "post";
        } else if (this instanceof PutVerbDefinition) {
            return "put";
        } else if (this instanceof DeleteVerbDefinition) {
            return "delete";
        } else if (this instanceof HeadVerbDefinition) {
            return "head";
        } else {
            return method;
        }
    }

}
