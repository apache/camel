package org.apache.camel.dsl.java.joor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dsl.support.CompilePostProcessor;
import org.apache.camel.dsl.support.ExtendedRouteBuilderLoaderSupport;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceAware;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Managed ClassRoutesBuilderLoader")
@RoutesLoader(ClassRoutesBuilderLoader.EXTENSION)
public class ClassRoutesBuilderLoader extends ExtendedRouteBuilderLoaderSupport {

    public static final String EXTENSION = "class";

    private static final Logger LOG = LoggerFactory.getLogger(ClassRoutesBuilderLoader.class);

    public ClassRoutesBuilderLoader() {
        super(EXTENSION);
    }

    @Override
    protected Collection<RoutesBuilder> doLoadRoutesBuilders(Collection<Resource> resources) throws Exception {
        Collection<RoutesBuilder> answer = new ArrayList<>();

        // load all the byte code first from the resources
        Map<String, byte[]> byteCodes = new LinkedHashMap<>();
        for (Resource res : resources) {
            String className = asClassName(res);
            InputStream is = res.getInputStream();
            if (is != null) {
                byte[] code = is.readAllBytes();
                byteCodes.put(className, code);
            }
        }

        MultiCompile.ByteArrayClassLoader cl = new MultiCompile.ByteArrayClassLoader(byteCodes);

        // instantiate classes from the byte codes
        for (Resource res : resources) {
            String className = asClassName(res);
            Class<?> clazz = cl.findClass(className);

            Object obj;
            try {
                // requires a default no-arg constructor otherwise we skip the class
                obj = getCamelContext().getInjector().newInstance(clazz);
            } catch (Exception e) {
                LOG.debug("Loaded class: " + className + " must have a default no-arg constructor. Skipping.");
                continue;
            }

            // inject context and resource
            CamelContextAware.trySetCamelContext(obj, getCamelContext());
            ResourceAware.trySetResource(obj, res);

            // support custom annotation scanning post compilation
            // such as to register custom beans, type converters, etc.
            for (CompilePostProcessor pre : getCompilePostProcessors()) {
                pre.postCompile(getCamelContext(), className, clazz, null, obj);
            }

            if (obj instanceof RouteBuilder) {
                RouteBuilder builder = (RouteBuilder) obj;
                answer.add(builder);
            }
        }

        return answer;
    }

    private static String asClassName(Resource resource) {
        String className = resource.getLocation();
        className = className.replace('/', '.');
        if (className.endsWith(".class")) {
            className = className.substring(0, className.length() - 6);
        }
        return className;
    }
}
