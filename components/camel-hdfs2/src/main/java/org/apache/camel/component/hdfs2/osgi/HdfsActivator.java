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
package org.apache.camel.component.hdfs2.osgi;

import org.apache.hadoop.util.ShutdownHookManager;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class HdfsActivator implements BundleActivator {

    @Override
    public void start(BundleContext context) throws Exception {
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        // There's problem inside OSGi when framwork is being shutdown
        // hadoop.fs code registers some JVM shutdown hooks throughout the code and this ordered
        // list of hooks is run in shutdown thread.
        // At that time bundle class loader / bundle wiring is no longer valid (bundle is stopped)
        // so ShutdownHookManager can't load additional classes. But there are some inner classes
        // loaded when iterating over registered hadoop shutdown hooks.
        // Let's explicitely load these inner classes when bundle is stopped, as there's last chance
        // to use valid bundle class loader.
        // This is based on the knowledge of what's contained in SMX bundle
        // org.apache.servicemix.bundles.hadoop-client-*.jar
        // the above is just a warning that hadopp may have some quirks when running inside OSGi
        ClassLoader hadoopCl = ShutdownHookManager.class.getClassLoader();
        if (hadoopCl != null) {
            String shm = ShutdownHookManager.class.getName();
            hadoopCl.loadClass(shm + "$1");
            hadoopCl.loadClass(shm + "$2");
            hadoopCl.loadClass(shm + "$HookEntry");
        }
    }

}
