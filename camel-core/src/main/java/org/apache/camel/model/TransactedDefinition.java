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

import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Processor;
import org.apache.camel.spi.Policy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.TransactedPolicy;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents an XML &lt;transacted/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "transacted")
@XmlAccessorType(XmlAccessType.FIELD)
public class TransactedDefinition extends OutputDefinition<ProcessorDefinition> {

    // TODO: Align this code with PolicyDefinition

    // JAXB does not support changing the ref attribute from required to optional
    // if we extend PolicyDefinition so we must make a copy of the class
    @XmlTransient
    public static final String PROPAGATION_REQUIRED = "PROPAGATION_REQUIRED";
    @XmlTransient
    protected Class<? extends Policy> type = TransactedPolicy.class;
    @XmlAttribute
    protected String ref;
    @XmlTransient
    private Policy policy;

    public TransactedDefinition() {
    }

    public TransactedDefinition(Policy policy) {
        this.policy = policy;
    }

    @Override
    public String toString() {
        return "Transacted[" + description() + "]";
    }

    @Override
    public String getShortName() {
        return "transacted";
    }

    @Override
    public String getLabel() {
        if (ref != null) {
            return "ref: " + ref;
        } else if (policy != null) {
            return policy.toString();
        } else {
            return "";
        }
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    /**
     * Sets a policy type that this defition should scope within.
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
    public TransactedDefinition ref(String ref) {
        setRef(ref);
        return this;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = createOutputsProcessor(routeContext);

        Policy policy = resolvePolicy(routeContext);
        ObjectHelper.notNull(policy, "policy", this);
        return policy.wrap(routeContext, childProcessor);
    }

    protected Policy resolvePolicy(RouteContext routeContext) {
        if (policy == null) {
            // try ref first
            String ref = getRef();
            if (ObjectHelper.isNotEmpty(ref)) {
                policy = routeContext.lookup(ref, Policy.class);
            }

            // try to lookup by scoped type
            if (policy == null && type != null) {
                // try find by type, note that this method is not supported by all registry
                Map types = routeContext.lookupByType(type);
                if (types.size() == 1) {
                    // only one policy defined so use it
                    Object found = types.values().iterator().next();
                    if (type.isInstance(found)) {
                        return type.cast(found);
                    }
                }
            }

            // for transacted routing try the default REQUIRED name
            if (policy == null && type == TransactedPolicy.class) {
                // still not found try with the default name PROPAGATION_REQUIRED
                policy = routeContext.lookup(PROPAGATION_REQUIRED, TransactedPolicy.class);
            }
        }
        return policy;
    }

    protected String description() {
        if (policy != null) {
            return policy.toString();
        } else {
            return "ref: " + ref;
        }
    }
}
