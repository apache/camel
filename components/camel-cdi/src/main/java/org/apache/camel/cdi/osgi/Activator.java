package org.apache.camel.cdi.osgi;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import javax.enterprise.inject.spi.Extension;
import java.util.Dictionary;
import java.util.Hashtable;

import static org.osgi.framework.Constants.SERVICE_DESCRIPTION;
import static org.osgi.framework.Constants.SERVICE_VENDOR;

public class Activator implements BundleActivator {

    // instead of importing org.osgi.service.cdi.CDIConstants#CDI_EXTENSION_PROPERTY from mvn:org.osgi/org.osgi.service.cdi/1.0.1
    public static final String CDI_EXTENSION_PROPERTY = "osgi.cdi.extension";

    @Override
    public void start(BundleContext context) throws Exception {
        Dictionary<String, Object> properties = new Hashtable<>();
        properties.put(CDI_EXTENSION_PROPERTY, "org.apache.camel.cdi");
        properties.put(SERVICE_DESCRIPTION, "Camel CDI Portable Extension");
        properties.put(SERVICE_VENDOR, "Apache");

        _serviceRegistration = context.registerService(
                Extension.class, new CdiCamelExtensionFactory(), properties);

    }

    @Override
    public void stop(BundleContext context) throws Exception {
        _serviceRegistration.unregister();
    }


    private ServiceRegistration<Extension> _serviceRegistration;

}
