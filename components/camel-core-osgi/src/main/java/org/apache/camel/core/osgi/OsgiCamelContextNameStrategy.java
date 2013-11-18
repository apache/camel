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
package org.apache.camel.core.osgi;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.CamelContextNameStrategy;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.core.osgi.OsgiCamelContextPublisher.CONTEXT_NAME_PROPERTY;

/**
 * In OSGi we want to use a {@link CamelContextNameStrategy} that finds a free name in the
 * OSGi Service Registry to be used for auto assigned names.
 * <p/>
 * If there is a name clash in the OSGi registry, then a new candidate name is used by appending
 * a unique counter.
 */
public class OsgiCamelContextNameStrategy implements CamelContextNameStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(OsgiCamelContextNameStrategy.class);
    private static final AtomicInteger CONTEXT_COUNTER = new AtomicInteger(0);
    private final BundleContext context;
    private final String prefix = "camel";
    private volatile String name;

    public OsgiCamelContextNameStrategy(BundleContext context) {
        this.context = context;
    }

    @Override
    public String getName() {
        if (name == null) {
            name = getNextName();
        }
        return name;
    }

    @Override
    public synchronized String getNextName() {
        // generate new candidate
        String candidate = prefix + "-" + getNextCounter();

        boolean clash = false;
        do {
            try {
                LOG.trace("Checking OSGi Service Registry for existence of existing CamelContext with name: {}", candidate);
                ServiceReference[] refs = context.getServiceReferences(CamelContext.class.getName(), "(" + CONTEXT_NAME_PROPERTY + "=" + candidate + ")");
                clash = refs != null && refs.length > 0;
                if (clash) {
                    // generate new candidate
                    candidate = prefix + "-" + getNextCounter();
                }
            } catch (InvalidSyntaxException e) {
                LOG.debug("Error finding free Camel name in OSGi Service Registry due " + e.getMessage() + ". This exception is ignored.", e);
                break;
            }
        } while (clash);

        LOG.debug("Generated CamelContext name for bundle id: {}, clash: {} -> {}", new Object[]{context.getBundle().getBundleId(), clash, name});
        return candidate;
    }

    @Override
    public boolean isFixedName() {
        return false;
    }

    public static int getNextCounter() {
        // we want to start counting from 1, so increment first
        return CONTEXT_COUNTER.incrementAndGet();
    }

}
