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
package org.apache.camel.cdi;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.InjectionException;
import javax.enterprise.inject.spi.Producer;

import org.apache.camel.CamelContext;
import org.apache.camel.core.osgi.OsgiCamelContextHelper;
import org.apache.camel.core.osgi.OsgiCamelContextPublisher;
import org.apache.camel.core.osgi.utils.BundleContextUtils;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultCamelContextNameStrategy;
import org.apache.camel.spi.CamelContextNameStrategy;
import org.osgi.framework.BundleContext;

final class CamelContextOsgiProducer<T extends CamelContext> extends DelegateProducer<T> {

    CamelContextOsgiProducer(Producer<T> delegate) {
        super(delegate);
    }

    @Override
    public T produce(CreationalContext<T> ctx) {
        T context = super.produce(ctx);

        // Register the context in the OSGi registry
        BundleContext bundle = BundleContextUtils.getBundleContext(getClass());
        context.getManagementStrategy().addEventNotifier(new OsgiCamelContextPublisher(bundle));

        if (!(context instanceof DefaultCamelContext)) {
            // Fail fast for the time being to avoid side effects by some methods get declared on the CamelContext interface
            throw new InjectionException("Camel CDI requires Camel context [" + context.getName() + "] to be a subtype of DefaultCamelContext");
        }

        DefaultCamelContext adapted = context.adapt(DefaultCamelContext.class);
        adapted.setRegistry(OsgiCamelContextHelper.wrapRegistry(context, context.getRegistry(), bundle));
        CamelContextNameStrategy strategy = context.getNameStrategy();
        OsgiCamelContextHelper.osgiUpdate(adapted, bundle);
        // FIXME: the above call should not override explicit strategies provided by the end user or should decorate them instead of overriding them completely
        if (!(strategy instanceof DefaultCamelContextNameStrategy)) {
            context.setNameStrategy(strategy);
        }

        return context;
    }
}
