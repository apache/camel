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

import org.apache.camel.Processor;
import org.apache.camel.impl.RouteContext;
import org.apache.camel.spi.Policy;

/**
 * @version $Revision: 1.1 $
 */
@XmlRootElement(name = "policy")
@XmlAccessorType(XmlAccessType.FIELD)
public class PolicyRef extends OutputType {
    @XmlAttribute(required = true)
    private String ref;
    @XmlTransient
    private Policy policy;

    public PolicyRef() {
    }

    public PolicyRef(Policy policy) {
        this.policy = policy;
    }

    @Override
    public String toString() {
        return "Policy[" + description() + "]";
    }

    @Override
    public String getLabel() {
        if (ref != null) {
            return "ref:  " + ref;
        }
        else if (policy != null) {
            return policy.toString();
        }
        else {
            return "";
        }
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = createOutputsProcessor(routeContext);

        Policy policy = resolvePolicy(routeContext);
        if (policy == null) {
            throw new IllegalArgumentException("No policy configured: " + this);
        }
        return policy.wrap(childProcessor);
    }

    protected Policy resolvePolicy(RouteContext routeContext) {
        if (policy == null) {
            policy = routeContext.lookup(getRef(), Policy.class);
        }
        return policy;
    }

    protected String description() {
        if (policy != null) {
            return policy.toString();
        } else {
            return "ref:  " + ref;
        }
    }
}
