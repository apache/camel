/*
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
package org.apache.camel.component.cedar;

import java.util.IdentityHashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;

@Metadata(label = "bean",
          description = "Embedded Cedar authorization before a route segment. Preserves the body and fails closed on deny or error.",
          annotations = { "interfaceName=org.apache.camel.spi.AuthorizationPolicy" })
@Configurer(metadataOnly = true)
public class CedarSecurityPolicy extends CedarConfiguration implements AuthorizationPolicy {
    private final Map<CamelContext, Owner> owners = new IdentityHashMap<>();

    private static final class Owner {
        private final CedarEvaluator evaluator;
        private int users;

        private Owner(CedarEvaluator evaluator) {
            this.evaluator = evaluator;
        }
    }

    synchronized CedarEvaluator acquire(CamelContext context) throws Exception {
        if (!owners.isEmpty() && !owners.containsKey(context)) {
            throw new IllegalStateException("Use a separate CedarSecurityPolicy bean for each CamelContext");
        }
        Owner owner = owners.get(context);
        if (owner == null) {
            CedarEvaluator evaluator = new CedarEvaluator(context, this);
            evaluator.start();
            owner = new Owner(evaluator);
            owners.put(context, owner);
        }
        owner.users++;
        return owner.evaluator;
    }

    synchronized void release(CamelContext context) throws Exception {
        Owner owner = owners.get(context);
        if (owner != null && --owner.users == 0) {
            owners.remove(context);
            owner.evaluator.stop();
        }
    }

    /** Validate and reload each active context's evaluator. Every exchange uses one coherent revision. */
    public synchronized void reload() throws Exception {
        if (owners.isEmpty()) {
            throw new IllegalStateException("Cedar security policy has no started routes");
        }
        for (Owner owner : owners.values()) {
            owner.evaluator.reload();
        }
    }

    @Override
    public void beforeWrap(Route route, NamedNode definition) {
    }

    @Override
    public Processor wrap(Route route, Processor processor) {
        return new CedarSecurityProcessor(processor, this, route.getCamelContext());
    }
}
