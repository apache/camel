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
package org.apache.camel.component.zookeepermaster.group.internal.osgi;

import org.apache.camel.component.zookeepermaster.group.NodeState;
import org.apache.camel.component.zookeepermaster.group.internal.DelegateZooKeeperGroup;
import org.apache.curator.framework.CuratorFramework;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

public class TrackingZooKeeperGroup<T extends NodeState> extends DelegateZooKeeperGroup<T> {

    private final BundleContext bundleContext;
    private final ServiceTracker<CuratorFramework, CuratorFramework> tracker;

    public TrackingZooKeeperGroup(BundleContext bundleContext, String path, Class<T> clazz) {
        super(path, clazz);
        this.bundleContext = bundleContext;
        this.tracker = new ServiceTracker<>(bundleContext, CuratorFramework.class, new ServiceTrackerCustomizer<CuratorFramework, CuratorFramework>() {
            @Override
            public CuratorFramework addingService(ServiceReference<CuratorFramework> reference) {
                CuratorFramework curator = TrackingZooKeeperGroup.this.bundleContext.getService(reference);
                useCurator(curator);
                return curator;
            }

            @Override
            public void modifiedService(ServiceReference<CuratorFramework> reference, CuratorFramework service) {
            }

            @Override
            public void removedService(ServiceReference<CuratorFramework> reference, CuratorFramework service) {
                useCurator(null);
                TrackingZooKeeperGroup.this.bundleContext.ungetService(reference);
            }
        });
    }

    @Override
    protected void doStart() {
        tracker.open();
    }

    @Override
    protected void doStop() {
        tracker.close();
    }

}
