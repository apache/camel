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
package org.apache.camel.component.cmis.osgi;

import org.apache.camel.component.cmis.SessionFactoryLocator;
import org.apache.chemistry.opencmis.client.api.SessionFactory;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

// Locator the SessionFactoryImpl service
public class Activator implements BundleActivator {
    private ServiceReference reference;

    @Override
    public void start(BundleContext context) throws Exception {
        // get the reference of SessionFactory service
        reference = context.getServiceReference(SessionFactory.class.getName());
        SessionFactory sessionFactory = (SessionFactory)context.getService(reference);
        SessionFactoryLocator.setSessionFactory(sessionFactory);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        // release the reference
        if (reference != null) {
            context.ungetService(reference);
        }
    }

}
