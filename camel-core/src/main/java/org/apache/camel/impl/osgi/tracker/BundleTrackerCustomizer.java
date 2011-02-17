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

/*
 * Copyright (c) OSGi Alliance (2007, 2008). All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.impl.osgi.tracker;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;

/**
 * The <code>BundleTrackerCustomizer</code> interface allows a
 * <code>BundleTracker</code> to customize the <code>Bundle</code>s that are
 * tracked. A <code>BundleTrackerCustomizer</code> is called when a bundle is
 * being added to a <code>BundleTracker</code>. The
 * <code>BundleTrackerCustomizer</code> can then return an object for the
 * tracked bundle. A <code>BundleTrackerCustomizer</code> is also called when a
 * tracked bundle is modified or has been removed from a
 * <code>BundleTracker</code>.
 * 
 * <p>
 * The methods in this interface may be called as the result of a
 * <code>BundleEvent</code> being received by a <code>BundleTracker</code>.
 * Since <code>BundleEvent</code>s are received synchronously by the
 * <code>BundleTracker</code>, it is highly recommended that implementations of
 * these methods do not alter bundle states while being synchronized on any
 * object.
 * 
 * <p>
 * The <code>BundleTracker</code> class is thread-safe. It does not call a
 * <code>BundleTrackerCustomizer</code> while holding any locks.
 * <code>BundleTrackerCustomizer</code> implementations must also be
 * thread-safe.
 * 
 * @ThreadSafe
 * @version 
 * @since 1.4
 */
public interface BundleTrackerCustomizer {
    /**
     * A bundle is being added to the <code>BundleTracker</code>.
     * <p>
     * This method is called before a bundle which matched the search parameters
     * of the <code>BundleTracker</code> is added to the
     * <code>BundleTracker</code>. This method should return the object to be
     * tracked for the specified <code>Bundle</code>. The returned object is
     * stored in the <code>BundleTracker</code> and is available from the
     * {@link BundleTracker#getObject(Bundle) getObject} method.
     * 
     * @param bundle The <code>Bundle</code> being added to the
     *            <code>BundleTracker</code>.
     * @param event The bundle event which caused this customizer method to be
     *            called or <code>null</code> if there is no bundle event
     *            associated with the call to this method.
     * @return The object to be tracked for the specified <code>Bundle</code>
     *         object or <code>null</code> if the specified <code>Bundle</code>
     *         object should not be tracked.
     */
    Object addingBundle(Bundle bundle, BundleEvent event);

    /**
     * A bundle tracked by the <code>BundleTracker</code> has been modified.
     * <p>
     * This method is called when a bundle being tracked by the
     * <code>BundleTracker</code> has had its state modified.
     * 
     * @param bundle The <code>Bundle</code> whose state has been modified.
     * @param event The bundle event which caused this customizer method to be
     *            called or <code>null</code> if there is no bundle event
     *            associated with the call to this method.
     * @param object The tracked object for the specified bundle.
     */
    void modifiedBundle(Bundle bundle, BundleEvent event, Object object);

    /**
     * A bundle tracked by the <code>BundleTracker</code> has been removed.
     * <p>
     * This method is called after a bundle is no longer being tracked by the
     * <code>BundleTracker</code>.
     * 
     * @param bundle The <code>Bundle</code> that has been removed.
     * @param event The bundle event which caused this customizer method to be
     *            called or <code>null</code> if there is no bundle event
     *            associated with the call to this method.
     * @param object The tracked object for the specified bundle.
     */
    void removedBundle(Bundle bundle, BundleEvent event, Object object);
}
