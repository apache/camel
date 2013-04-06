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
package org.apache.camel.component.paxlogging;

import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;

/**
 * This service factory only purpose is to know which bundle is requesting the component
 * so that the pax logging appender can be registered from the correct bundle context.
 */
public class PaxLoggingServiceFactory implements ServiceFactory {

    public Object getService(Bundle bundle, ServiceRegistration serviceRegistration) {
        return new PaxLoggingComponentResolver(bundle.getBundleContext());
    }

    public void ungetService(Bundle bundle, ServiceRegistration serviceRegistration, Object o) {
    }

}
