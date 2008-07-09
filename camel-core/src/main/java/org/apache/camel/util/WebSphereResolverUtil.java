package org.apache.camel.util;

import java.net.URL;
import java.net.MalformedURLException;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.io.IOException;

import org.apache.camel.impl.converter.AnnotationTypeConverterLoader;

/**
 * WebSphere specific resolver util to handle loading annotated resources in JAR files.
 */
public class WebSphereResolverUtil extends ResolverUtil {

    /**
     * Is the classloader from IBM and thus the WebSphere platform?
     *
     * @param loader  the classloader
     * @return  <tt>true</tt> if IBM classloader, <tt>false</tt> otherwise.
     */
    public static boolean isWebSphereClassLoader(ClassLoader loader) {
        return loader.getClass().getName().startsWith("com.ibm");
    }

    /**
     * Overloaded to handle specific problem with getting resources on the IBM WebSphere platform.
     * <p/>
     * WebSphere can <b>not</b> load resources if the resource to load is a folder name, such as a
     * packagename, you have to explicit name a resource that is a file.
     *
     * @param loader  the classloader
     * @param packageName   the packagename for the package to load
     * @return  URL's for the given package
     * @throws IOException is thrown by the classloader
     */
    @Override
    protected Enumeration<URL> getResources(ClassLoader loader, String packageName) throws IOException {
        // try super first, just in vase
        Enumeration<URL> enumeration = super.getResources(loader, packageName);
        if (! enumeration.hasMoreElements()) {
            LOG.trace("Using WebSphere workaround to load the camel jars with the annotated converters.");
            // Special WebSphere trick to load a file that exists in the JAR and then let it go from there.
            // The trick is that we just need the URL's for the .jars that contains the type
            // converters that is annotated. So by searching for this resource WebSphere is able to find
            // it and return the URL to the .jar file with the resource. Then the default ResolverUtil
            // can take it from there and find the classes that are annotated.
            enumeration = loader.getResources(AnnotationTypeConverterLoader.META_INF_SERVICES);
        }

        return enumeration;
    }

}
