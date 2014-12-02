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
package org.apache.camel.itest.osgi.blueprint;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;

import javax.inject.Inject;

import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.ops4j.pax.exam.CoreOptions;

import org.ops4j.pax.exam.options.MavenArtifactProvisionOption;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

public abstract class AbstractIntegrationTest extends OSGiIntegrationTestSupport {

    public static final long DEFAULT_TIMEOUT = 30000;

    @Inject
    protected BundleContext bundleContext;

    protected <T> T getOsgiService(Class<T> type, long timeout) {
        return getOsgiService(type, null, timeout);
    }

    protected <T> T getOsgiService(Class<T> type) {
        return getOsgiService(type, null, DEFAULT_TIMEOUT);
    }

    protected <T> T getOsgiService(Class<T> type, String filter, long timeout) {
        ServiceTracker<T, T> tracker;
        try {
            String flt;
            if (filter != null) {
                if (filter.startsWith("(")) {
                    flt = "(&(" + Constants.OBJECTCLASS + "=" + type.getName() + ")" + filter + ")";
                } else {
                    flt = "(&(" + Constants.OBJECTCLASS + "=" + type.getName() + ")(" + filter + "))";
                }
            } else {
                flt = "(" + Constants.OBJECTCLASS + "=" + type.getName() + ")";
            }
            Filter osgiFilter = FrameworkUtil.createFilter(flt);
            tracker = new ServiceTracker<T, T>(bundleContext, osgiFilter, null);
            tracker.open(true);
            // Note that the tracker is not closed to keep the reference
            // This is buggy, as the service reference may change i think
            T svc = tracker.waitForService(timeout);
            if (svc == null) {
                @SuppressWarnings("rawtypes")
                Dictionary dic = bundleContext.getBundle().getHeaders();
                LOG.warn("Test bundle headers: " + explode(dic));

                for (ServiceReference<?> ref : asCollection(bundleContext.getAllServiceReferences(null, null))) {
                    LOG.warn("ServiceReference: " + ref);
                }

                for (ServiceReference<?> ref : asCollection(bundleContext.getAllServiceReferences(null, flt))) {
                    LOG.warn("Filtered ServiceReference: " + ref);
                }

                throw new RuntimeException("Gave up waiting for service " + flt);
            }
            return svc;
        } catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Invalid filter", e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected Bundle installBundle(String groupId, String artifactId) throws Exception {
        MavenArtifactProvisionOption mvnUrl = mavenBundle(groupId, artifactId);
        return bundleContext.installBundle(mvnUrl.getURL());
    }

    protected Bundle getInstalledBundle(String symbolicName) {
        for (Bundle b : bundleContext.getBundles()) {
            if (b.getSymbolicName().equals(symbolicName)) {
                return b;
            }
        }
        for (Bundle b : bundleContext.getBundles()) {
            LOG.warn("Bundle: " + b.getSymbolicName());
        }
        throw new RuntimeException("Bundle " + symbolicName + " does not exist");
    }

    /*
     * Explode the dictionary into a ,-delimited list of key=value pairs
     */
    private static String explode(Dictionary<?, ?> dictionary) {
        Enumeration<?> keys = dictionary.keys();
        StringBuilder result = new StringBuilder();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            result.append(String.format("%s=%s", key, dictionary.get(key)));
            if (keys.hasMoreElements()) {
                result.append(", ");
            }
        }
        return result.toString();
    }

    /*
     * Provides an iterable collection of references, even if the original array is null
     */
    private static Collection<ServiceReference<?>> asCollection(ServiceReference<?>[] references) {
        return references != null ? Arrays.asList(references) : Collections.<ServiceReference<?>>emptyList();
    }

    /**
     *  Create an provisioning option for the specified maven artifact
     * (groupId and artifactId), using the version found in the list
     * of dependencies of this maven project.
     *
     * @param groupId the groupId of the maven bundle
     * @param artifactId the artifactId of the maven bundle
     * @return the provisioning option for the given bundle
     */
    protected static MavenArtifactProvisionOption mavenBundle(String groupId, String artifactId) {
        return CoreOptions.mavenBundle(groupId, artifactId).versionAsInProject();
    }
    
    /**
     *  Create an provisioning option for the specified maven artifact
     * (groupId and artifactId), using the version found in the list
     * of dependencies of this maven project.
     *
     * @param groupId the groupId of the maven bundle
     * @param artifactId the artifactId of the maven bundle
     * @param version the version of the maven bundle
     * @return the provisioning option for the given bundle
     */
    protected static MavenArtifactProvisionOption mavenBundle(String groupId, String artifactId, String version) {
        return CoreOptions.mavenBundle(groupId, artifactId).version(version);
    }

}