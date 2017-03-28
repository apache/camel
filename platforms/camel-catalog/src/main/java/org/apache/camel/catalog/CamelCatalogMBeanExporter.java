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
package org.apache.camel.catalog;

import java.lang.management.ManagementFactory;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

/**
 * MBean exporter to register the {@link CamelCatalog} in JMX.
 */
public class CamelCatalogMBeanExporter {

    public static final String MBEAN_NAME = "org.apache.camel.catalog:type=catalog,name=catalog";

    private CamelCatalog catalog;
    private ObjectName objectName;
    private MBeanServer mBeanServer;

    /**
     * Initializes and exports the {@link CamelCatalog} in JMX using the domain name,
     * which can be obtained using {@link #getObjectName()}.
     *
     * @throws Exception is thrown if error during registration
     */
    public void init() throws Exception {
        catalog = new DefaultCamelCatalog();

        if (objectName == null) {
            objectName = getObjectName();
        }

        if (mBeanServer == null) {
            mBeanServer = ManagementFactory.getPlatformMBeanServer();
        }

        if (mBeanServer != null) {
            try {
                // notice some mbean servers may register using a changed object name
                ObjectInstance oi = mBeanServer.registerMBean(catalog, objectName);
                if (oi != null && oi.getObjectName() != null) {
                    objectName = oi.getObjectName();
                }
            } catch (InstanceAlreadyExistsException iaee) {
                // Try to remove and re-register
                mBeanServer.unregisterMBean(objectName);
                mBeanServer.registerMBean(catalog, objectName);
            }
        }
    }

    /**
     * Destroys and un-registers the {@link CamelCatalog} from JMX.
     *
     * @throws Exception is thrown if error during un-registration
     */
    public void destroy() throws Exception {
        if (mBeanServer != null) {
            if (objectName != null) {
                mBeanServer.unregisterMBean(objectName);
            }
        }
    }

    protected ObjectName getObjectName() throws Exception {
        return new ObjectName(MBEAN_NAME);
    }

}
