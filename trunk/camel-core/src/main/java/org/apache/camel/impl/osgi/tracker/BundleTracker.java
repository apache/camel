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
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;

/**
 * The <code>BundleTracker</code> class simplifies tracking bundles much like
 * the <code>ServiceTracker</code> simplifies tracking services.
 * <p>
 * A <code>BundleTracker</code> is constructed with state criteria and a
 * <code>BundleTrackerCustomizer</code> object. A <code>BundleTracker</code> can
 * use the <code>BundleTrackerCustomizer</code> to select which bundles are
 * tracked and to create a customized object to be tracked with the bundle. The
 * <code>BundleTracker</code> can then be opened to begin tracking all bundles
 * whose state matches the specified state criteria.
 * <p>
 * The <code>getBundles</code> method can be called to get the
 * <code>Bundle</code> objects of the bundles being tracked. The
 * <code>getObject</code> method can be called to get the customized object for
 * a tracked bundle.
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
public class BundleTracker implements BundleTrackerCustomizer {
    /* set this to true to compile in debug messages */
    static final boolean DEBUG = false;
 
    /**
     * The Bundle Context used by this <code>BundleTracker</code>.
     */
    protected final BundleContext context;
    
    /**
     * State mask for bundles being tracked. This field contains the ORed values
     * of the bundle states being tracked.
     */
    final int mask;
    
    /**
     * The <code>BundleTrackerCustomizer</code> object for this tracker.
     */
    final BundleTrackerCustomizer customizer;

    /**
     * Tracked bundles: <code>Bundle</code> object -> customized Object and
     * <code>BundleListener</code> object
     */
    private volatile Tracked tracked;

    /**
     * Create a <code>BundleTracker</code> for bundles whose state is present in
     * the specified state mask.
     * <p>
     * Bundles whose state is present on the specified state mask will be
     * tracked by this <code>BundleTracker</code>.
     * 
     * @param context The <code>BundleContext</code> against which the tracking
     *            is done.
     * @param stateMask The bit mask of the <code>OR</code>ing of the bundle
     *            states to be tracked.
     * @param customizer The customizer object to call when bundles are added,
     *            modified, or removed in this <code>BundleTracker</code>. If
     *            customizer is <code>null</code>, then this
     *            <code>BundleTracker</code> will be used as the
     *            <code>BundleTrackerCustomizer</code> and this
     *            <code>BundleTracker</code> will call the
     *            <code>BundleTrackerCustomizer</code> methods on itself.
     * @see Bundle#getState()
     */
    public BundleTracker(BundleContext context, int stateMask, BundleTrackerCustomizer customizer) {
        this.context = context;
        this.mask = stateMask;
        this.customizer = (customizer == null) ? this : customizer;
    }
    
    /**
     * Accessor method for the current Tracked object. This method is only
     * intended to be used by the unsynchronized methods which do not modify the
     * tracked field.
     * 
     * @return The current Tracked object.
     */
    private Tracked tracked() {
        return tracked;
    }


    /**
     * Open this <code>BundleTracker</code> and begin tracking bundles.
     * <p>
     * Bundle which match the state criteria specified when this
     * <code>BundleTracker</code> was created are now tracked by this
     * <code>BundleTracker</code>.
     * 
     * @throws java.lang.IllegalStateException If the <code>BundleContext</code>
     *             with which this <code>BundleTracker</code> was created is no
     *             longer valid.
     * @throws java.lang.SecurityException If the caller and this class do not
     *             have the appropriate
     *             <code>AdminPermission[context bundle,LISTENER]</code>, and
     *             the Java Runtime Environment supports permissions.
     */
    public void open() {
        final Tracked t;
        synchronized (this) {
            if (tracked != null) {
                return;
            }
            if (DEBUG) {
                System.out.println("BundleTracker.open");
            }
            t = new Tracked();
            synchronized (t) {
                context.addBundleListener(t);
                Bundle[] bundles = context.getBundles();
                if (bundles != null) {
                    int length = bundles.length;
                    for (int i = 0; i < length; i++) {
                        int state = bundles[i].getState();
                        if ((state & mask) == 0) {
                            /* null out bundles whose states are not interesting */
                            bundles[i] = null;
                        }
                    }
                    /* set tracked with the initial bundles */
                    t.setInitial(bundles);
                }
            }
            tracked = t;
        }
        /* Call tracked outside of synchronized region */
        t.trackInitial(); /* process the initial references */
    }

    /**
     * Close this <code>BundleTracker</code>.
     * <p>
     * This method should be called when this <code>BundleTracker</code> should
     * end the tracking of bundles.
     * <p>
     * This implementation calls {@link #getBundles()} to get the list of
     * tracked bundles to remove.
     */
    public void close() {
        final Bundle[] bundles;
        final Tracked outgoing;
        synchronized (this) {
            outgoing = tracked;
            if (outgoing == null) {
                return;
            }
            if (DEBUG) {
                System.out.println("BundleTracker.close");
            }
            outgoing.close();
            bundles = getBundles();
            tracked = null;
            try {
                context.removeBundleListener(outgoing);
            } catch (IllegalStateException e) {
                /* In case the context was stopped. */
            }
        }
        if (bundles != null) {
            for (Bundle bundle : bundles) {
                outgoing.untrack(bundle, null);
            }
        }
    }

    /**
     * Default implementation of the
     * <code>BundleTrackerCustomizer.addingBundle</code> method.
     * <p>
     * This method is only called when this <code>BundleTracker</code> has been
     * constructed with a <code>null BundleTrackerCustomizer</code> argument.
     * <p>
     * This implementation simply returns the specified <code>Bundle</code>.
     * <p>
     * This method can be overridden in a subclass to customize the object to be
     * tracked for the bundle being added.
     * 
     * @param bundle The <code>Bundle</code> being added to this
     *            <code>BundleTracker</code> object.
     * @param event The bundle event which caused this customizer method to be
     *            called or <code>null</code> if there is no bundle event
     *            associated with the call to this method.
     * @return The specified bundle.
     * @see BundleTrackerCustomizer#addingBundle(Bundle, BundleEvent)
     */
    public Object addingBundle(Bundle bundle, BundleEvent event) {
        return bundle;
    }

    /**
     * Default implementation of the
     * <code>BundleTrackerCustomizer.modifiedBundle</code> method.
     * <p>
     * This method is only called when this <code>BundleTracker</code> has been
     * constructed with a <code>null BundleTrackerCustomizer</code> argument.
     * <p>
     * This implementation does nothing.
     * 
     * @param bundle The <code>Bundle</code> whose state has been modified.
     * @param event The bundle event which caused this customizer method to be
     *            called or <code>null</code> if there is no bundle event
     *            associated with the call to this method.
     * @param object The customized object for the specified Bundle.
     * @see BundleTrackerCustomizer#modifiedBundle(Bundle, BundleEvent, Object)
     */
    public void modifiedBundle(Bundle bundle, BundleEvent event, Object object) {
        /* do nothing */
    }

    /**
     * Default implementation of the
     * <code>BundleTrackerCustomizer.removedBundle</code> method.
     * <p>
     * This method is only called when this <code>BundleTracker</code> has been
     * constructed with a <code>null BundleTrackerCustomizer</code> argument.
     * <p>
     * This implementation does nothing.
     * 
     * @param bundle The <code>Bundle</code> being removed.
     * @param event The bundle event which caused this customizer method to be
     *            called or <code>null</code> if there is no bundle event
     *            associated with the call to this method.
     * @param object The customized object for the specified bundle.
     * @see BundleTrackerCustomizer#removedBundle(Bundle, BundleEvent, Object)
     */
    public void removedBundle(Bundle bundle, BundleEvent event, Object object) {
        /* do nothing */
    }

    /**
     * Return an array of <code>Bundle</code>s for all bundles being tracked by
     * this <code>BundleTracker</code>.
     * 
     * @return An array of <code>Bundle</code>s or <code>null</code> if no
     *         bundles are being tracked.
     */
    public Bundle[] getBundles() {
        final Tracked t = tracked();
        if (t == null) { /* if BundleTracker is not open */
            return null;
        }
        synchronized (t) {
            int length = t.size();
            if (length == 0) {
                return null;
            }
            return (Bundle[])t.getTracked(new Bundle[length]);
        }
    }

    /**
     * Returns the customized object for the specified <code>Bundle</code> if
     * the specified bundle is being tracked by this <code>BundleTracker</code>.
     * 
     * @param bundle The <code>Bundle</code> being tracked.
     * @return The customized object for the specified <code>Bundle</code> or
     *         <code>null</code> if the specified <code>Bundle</code> is not
     *         being tracked.
     */
    public Object getObject(Bundle bundle) {
        final Tracked t = tracked();
        if (t == null) { /* if BundleTracker is not open */
            return null;
        }
        synchronized (t) {
            return t.getCustomizedObject(bundle);
        }
    }

    /**
     * Remove a bundle from this <code>BundleTracker</code>. The specified
     * bundle will be removed from this <code>BundleTracker</code> . If the
     * specified bundle was being tracked then the
     * <code>BundleTrackerCustomizer.removedBundle</code> method will be called
     * for that bundle.
     * 
     * @param bundle The <code>Bundle</code> to be removed.
     */
    public void remove(Bundle bundle) {
        final Tracked t = tracked();
        if (t == null) { /* if BundleTracker is not open */
            return;
        }
        t.untrack(bundle, null);
    }

    /**
     * Return the number of bundles being tracked by this
     * <code>BundleTracker</code>.
     * 
     * @return The number of bundles being tracked.
     */
    public int size() {
        final Tracked t = tracked();
        if (t == null) { /* if BundleTracker is not open */
            return 0;
        }
        synchronized (t) {
            return t.size();
        }
    }

    /**
     * Returns the tracking count for this <code>BundleTracker</code>. The
     * tracking count is initialized to 0 when this <code>BundleTracker</code>
     * is opened. Every time a bundle is added, modified or removed from this
     * <code>BundleTracker</code> the tracking count is incremented.
     * <p>
     * The tracking count can be used to determine if this
     * <code>BundleTracker</code> has added, modified or removed a bundle by
     * comparing a tracking count value previously collected with the current
     * tracking count value. If the value has not changed, then no bundle has
     * been added, modified or removed from this <code>BundleTracker</code>
     * since the previous tracking count was collected.
     * 
     * @return The tracking count for this <code>BundleTracker</code> or -1 if
     *         this <code>BundleTracker</code> is not open.
     */
    public int getTrackingCount() {
        final Tracked t = tracked();
        if (t == null) { /* if BundleTracker is not open */
            return -1;
        }
        synchronized (t) {
            return t.getTrackingCount();
        }
    }

    /**
     * Inner class which subclasses AbstractTracked. This class is the
     * <code>SynchronousBundleListener</code> object for the tracker.
     * 
     * @ThreadSafe
     * @since 1.4
     */
    class Tracked extends AbstractTracked implements SynchronousBundleListener {

        /**
         * <code>BundleListener</code> method for the <code>BundleTracker</code>
         * class. This method must NOT be synchronized to avoid deadlock
         * potential.
         * 
         * @param event <code>BundleEvent</code> object from the framework.
         */
        public void bundleChanged(final BundleEvent event) {
            /*
             * Check if we had a delayed call (which could happen when we
             * close).
             */
            if (closed) {
                return;
            }
            final Bundle bundle = event.getBundle();
            final int state = bundle.getState();
            if (DEBUG) {
                System.out.println("BundleTracker.Tracked.bundleChanged[" + state + "]: " + bundle);
            }

            if ((state & mask) != 0) {
                track(bundle, event);
                /*
                 * If the customizer throws an unchecked exception, it is safe
                 * to let it propagate
                 */
            } else {
                untrack(bundle, event);
                /*
                 * If the customizer throws an unchecked exception, it is safe
                 * to let it propagate
                 */
            }
        }

        /**
         * Call the specific customizer adding method. This method must not be
         * called while synchronized on this object.
         * 
         * @param item Item to be tracked.
         * @param related Action related object.
         * @return Customized object for the tracked item or <code>null</code>
         *         if the item is not to be tracked.
         */
        Object customizerAdding(final Object item, final Object related) {
            return customizer.addingBundle((Bundle)item, (BundleEvent)related);
        }

        /**
         * Call the specific customizer modified method. This method must not be
         * called while synchronized on this object.
         * 
         * @param item Tracked item.
         * @param related Action related object.
         * @param object Customized object for the tracked item.
         */
        void customizerModified(final Object item, final Object related, final Object object) {
            customizer.modifiedBundle((Bundle)item, (BundleEvent)related, object);
        }

        /**
         * Call the specific customizer removed method. This method must not be
         * called while synchronized on this object.
         * 
         * @param item Tracked item.
         * @param related Action related object.
         * @param object Customized object for the tracked item.
         */
        void customizerRemoved(final Object item, final Object related, final Object object) {
            customizer.removedBundle((Bundle)item, (BundleEvent)related, object);
        }
    }
}
