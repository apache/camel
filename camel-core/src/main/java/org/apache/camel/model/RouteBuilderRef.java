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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;routeBuilderRef/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "routeBuilderRef")
@XmlAccessorType(XmlAccessType.FIELD)
public class RouteBuilderRef extends IdentifiedType {
    @XmlAttribute(required = true)
    private String ref;

    public RouteBuilderRef() {
    }

    public RouteBuilderRef(String ref) {
        this.ref = ref;
    }

    @Override
    public String toString() {
        return "RouteBuilderRef[" + getRef() + "]";
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public RouteBuilder createRouteBuilder(CamelContext camelContext) {
        ObjectHelper.notNull(camelContext, "camelContext");
        ObjectHelper.notNull(ref, "ref");
        RouteBuilder builder = CamelContextHelper.lookup(camelContext, ref, RouteBuilder.class);
        return builder != null ? builder : CamelContextHelper.newInstance(camelContext, RouteBuilder.class);
    }
}