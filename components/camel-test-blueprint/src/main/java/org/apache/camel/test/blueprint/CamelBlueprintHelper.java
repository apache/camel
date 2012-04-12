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
package org.apache.camel.test.blueprint;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.jar.JarInputStream;

import de.kalpatec.pojosr.framework.PojoServiceRegistryFactoryImpl;
import de.kalpatec.pojosr.framework.launch.BundleDescriptor;
import de.kalpatec.pojosr.framework.launch.ClasspathScanner;
import de.kalpatec.pojosr.framework.launch.PojoServiceRegistry;
import de.kalpatec.pojosr.framework.launch.PojoServiceRegistryFactory;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.ops4j.pax.swissbox.tinybundles.core.TinyBundle;
import org.ops4j.pax.swissbox.tinybundles.core.TinyBundles;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit4.TestSupport.createDirectory;
import static org.apache.camel.test.junit4.TestSupport.deleteDirectory;

/**
 * Helper for using Blueprint with Camel.
 */
public final class CamelBlueprintHelper {

    public static final long DEFAULT_TIMEOUT = 30000;
    private static final transient Logger LOG = LoggerFactory.getLogger(CamelBlueprintHelper.class);

    private CamelBlueprintHelper() {
    }

    public static BundleContext createBundleContext(String name, String descriptors) throws Exception {
        deleteDirectory("target/bundles");
        createDirectory("target/bundles");

        // ensure pojosr stores bundles in an unique target directory
        System.setProperty("org.osgi.framework.storage", "target/bundles/" + System.currentTimeMillis());
        List<BundleDescriptor> bundles = getBundleDescriptors();
        TinyBundle bundle = createTestBundle(name, descriptors);

        // add ourself as a bundle
        String jarName = name.toLowerCase();
        bundles.add(getBundleDescriptor("target/bundles/" + jarName + ".jar", bundle));

        Map<String, List<BundleDescriptor>> config = new HashMap<String, List<BundleDescriptor>>();
        config.put(PojoServiceRegistryFactory.BUNDLE_DESCRIPTORS, bundles);

        PojoServiceRegistry reg = new PojoServiceRegistryFactoryImpl().newPojoServiceRegistry(config);
        return reg.getBundleContext();
    }

    public static void disposeBundleContext(BundleContext bundleContext) throws BundleException {
        try {
            if (bundleContext != null) {
                bundleContext.getBundle().stop();
            }
        } finally {
            System.clearProperty("org.osgi.framework.storage");
        }
    }

    public static <T> T getOsgiService(BundleContext bundleContext, Class<T> type, long timeout) {
        return getOsgiService(bundleContext, type, null, timeout);
    }

    public static <T> T getOsgiService(BundleContext bundleContext, Class<T> type) {
        return getOsgiService(bundleContext, type, null, DEFAULT_TIMEOUT);
    }

    public static <T> T getOsgiService(BundleContext bundleContext, Class<T> type, String filter) {
        return getOsgiService(bundleContext, type, filter, DEFAULT_TIMEOUT);
    }

    public static <T> T getOsgiService(BundleContext bundleContext, Class<T> type, String filter, long timeout) {
        ServiceTracker tracker = null;
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
            tracker = new ServiceTracker(bundleContext, osgiFilter, null);
            tracker.open(true);
            // Note that the tracker is not closed to keep the reference
            // This is buggy, as the service reference may change i think
            Object svc = tracker.waitForService(timeout);
            if (svc == null) {
                Dictionary<?, ?> dic = bundleContext.getBundle().getHeaders();
                System.err.println("Test bundle headers: " + explode(dic));

                for (ServiceReference<?> ref : asCollection(bundleContext.getAllServiceReferences(null, null))) {
                    System.err.println("ServiceReference: " + ref);
                }

                for (ServiceReference<?> ref : asCollection(bundleContext.getAllServiceReferences(null, flt))) {
                    System.err.println("Filtered ServiceReference: " + ref);
                }

                throw new RuntimeException("Gave up waiting for service " + flt);
            }
            return type.cast(svc);
        } catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Invalid filter", e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Explode the dictionary into a <code>,</code> delimited list of <code>key=value</code> pairs.
     */
    private static String explode(Dictionary<?, ?> dictionary) {
        Enumeration<?> keys = dictionary.keys();
        StringBuffer result = new StringBuffer();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            result.append(String.format("%s=%s", key, dictionary.get(key)));
            if (keys.hasMoreElements()) {
                result.append(", ");
            }
        }
        return result.toString();
    }

    /**
     * Provides an iterable collection of references, even if the original array is <code>null</code>.
     */
    private static Collection<ServiceReference<?>> asCollection(ServiceReference<?>[] references) {
        return references  == null ? new ArrayList<ServiceReference<?>>(0) : Arrays.asList(references);
    }

    private static TinyBundle createTestBundle(String name, String descriptors) throws FileNotFoundException {
        TinyBundle bundle = TinyBundles.newBundle();
        for (URL url : getBlueprintDescriptors(descriptors)) {
            LOG.info("Using Blueprint XML file: " + url.getFile());
            bundle.add("OSGI-INF/blueprint/blueprint-" + url.getFile().replace("/", "-"), url);
        }
        bundle.set("Manifest-Version", "2")
                .set("Bundle-ManifestVersion", "2")
                .set("Bundle-SymbolicName", name)
                .set("Bundle-Version", "0.0.0");
        return bundle;
    }

    /**
     * Gets list of bundle descriptors.
     *
     * @return List pointers to OSGi bundles.
     * @throws Exception If looking up the bundles fails.
     */
    private static List<BundleDescriptor> getBundleDescriptors() throws Exception {
        return new ClasspathScanner().scanForBundles("(Bundle-SymbolicName=*)");
    }

    /**
     * Gets the bundle descriptors as {@link URL} resources.
     *
     * @param descriptors the bundle descriptors, can be separated by comma
     * @return the bundle descriptors.
     * @throws FileNotFoundException is thrown if a bundle descriptor cannot be found
     */
    private static Collection<URL> getBlueprintDescriptors(String descriptors) throws FileNotFoundException {
        List<URL> answer = new ArrayList<URL>();
        String descriptor = descriptors;
        if (descriptor != null) {
            // there may be more resources separated by comma
            Iterator<Object> it = ObjectHelper.createIterator(descriptor);
            while (it.hasNext()) {
                String s = (String) it.next();
                // remove leading / to be able to load resource from the classpath
                s = FileUtil.stripLeadingSeparator(s);

                // if there is wildcards for *.xml then we need to find the urls from the package
                if (s.endsWith("*.xml")) {
                    String packageName = s.substring(0, s.length() - 5);
                    // remove trailing / to be able to load resource from the classpath
                    Enumeration<URL> urls = ObjectHelper.loadResourcesAsURL(packageName);
                    while (urls.hasMoreElements()) {
                        URL url = urls.nextElement();
                        File dir = new File(url.getFile());
                        if (dir.isDirectory()) {
                            File[] files = dir.listFiles();
                            if (files != null) {
                                for (File file : files) {
                                    if (file.isFile() && file.exists() && file.getName().endsWith(".xml")) {
                                        String name = packageName + file.getName();
                                        URL xmlUrl = ObjectHelper.loadResourceAsURL(name);
                                        if (xmlUrl != null) {
                                            answer.add(xmlUrl);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    URL url = ObjectHelper.loadResourceAsURL(s);
                    if (url == null) {
                        throw new FileNotFoundException("Resource " + s + " not found in classpath");
                    }
                    answer.add(url);
                }
            }
        } else {
            throw new IllegalArgumentException("No bundle descriptor configured. Override getBlueprintDescriptor() or getBlueprintDescriptors() method");
        }

        if (answer.isEmpty()) {
            throw new IllegalArgumentException("Cannot find any resources in classpath from descriptor " + descriptors);
        }
        return answer;
    }

    private static BundleDescriptor getBundleDescriptor(String path, TinyBundle bundle) throws Exception {
        File file = new File(path);
        FileOutputStream fos = new FileOutputStream(file, true);
        try {
            IOHelper.copy(bundle.build(), fos);
        } finally {
            IOHelper.close(fos);
        }

        FileInputStream fis = null;
        JarInputStream jis = null;
        try {
            fis = new FileInputStream(file);
            jis = new JarInputStream(fis);
            Map<String, String> headers = new HashMap<String, String>();
            for (Map.Entry<Object, Object> entry : jis.getManifest().getMainAttributes().entrySet()) {
                headers.put(entry.getKey().toString(), entry.getValue().toString());
            }

            return new BundleDescriptor(
                    bundle.getClass().getClassLoader(),
                    new URL("jar:" + file.toURI().toString() + "!/"),
                    headers);
        } finally {
            IOHelper.close(fis, jis);
        }
    }

}
