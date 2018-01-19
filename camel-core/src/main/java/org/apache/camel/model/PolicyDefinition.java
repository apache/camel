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
import org.apache.camel.Service;
import org.apache.camel.processor.WrapProcessor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Policy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.TransactedPolicy;
import org.apache.camel.util.ObjectHelper;

/**
 * Defines a policy the route will use
 *
 * @version 
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "policy")
@XmlAccessorType(XmlAccessType.FIELD)
public class PolicyDefinition extends OutputDefinition<PolicyDefinition> {

    // TODO: Align this code with TransactedDefinition

    @XmlTransient
    protected Class<? extends Policy> type;
    @XmlAttribute(required = true)
    protected String ref;
    @XmlTransient
    private Policy policy;

    public PolicyDefinition() {
    }

    public PolicyDefinition(Policy policy) {
        this.policy = policy;
    }

    @Override
    public String toString() {
        return "Policy[" + description() + "]";
    }
    
    protected String description() {
        if (policy != null) {
            return policy.toString();
        } else {
            return "ref:" + ref;
        }
    }

    @Override
    public String getShortName() {
        // a policy can be a hidden disguise for a transacted definition
        boolean transacted = type != null && type.isAssignableFrom(TransactedPolicy.class);
        return transacted ? "transacted" : "policy";
    }

    @Override
    public String getLabel() {
        return getShortName() + "[" + getDescription() + "]";
    }

    @Override
    public boolean isAbstract() {
        // policy should NOT be abstract
        return false;
    }

    @Override
    public boolean isTopLevelOnly() {
        // a policy is often top-level but you can have it in lower-levels as well
        return false;
    }

    @Override
    public boolean isWrappingEntireOutput() {
        return true;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    /**
     * Sets a policy type that this definition should scope within.
     * <p/>
     * Is used for convention over configuration situations where the policy
     * should be automatic looked up in the registry and it should be based
     * on this type. For instance a {@link org.apache.camel.spi.TransactedPolicy}
     * can be set as type for easy transaction configuration.
     * <p/>
     * Will by default scope to the wide {@link Policy}
     *
     * @param type the policy type
     */
    public void setType(Class<? extends Policy> type) {
        this.type = type;
    }

    /**
     * Sets a reference to use for lookup the policy in the registry.
     *
     * @param ref the reference
     * @return the builder
     */
    public PolicyDefinition ref(String ref) {
        setRef(ref);
        return this;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Policy policy = resolvePolicy(routeContext);
        ObjectHelper.notNull(policy, "policy", this);

        // before wrap
        policy.beforeWrap(routeContext, this);

        // create processor after the before wrap
        Processor childProcessor = this.createChildProcessor(routeContext, true);

        // wrap
        Processor target = policy.wrap(routeContext, childProcessor);

        if (!(target instanceof Service)) {
            // wrap the target so it becomes a service and we can manage its lifecycle
            target = new WrapProcessor(target, childProcessor);
        }
        return target;
    }

    protected Policy resolvePolicy(RouteContext routeContext) {
        if (policy != null) {
            return policy;
        }
        // reuse code on transacted definition to do the resolution
        return TransactedDefinition.doResolvePolicy(routeContext, getRef(), type);
    }

}
