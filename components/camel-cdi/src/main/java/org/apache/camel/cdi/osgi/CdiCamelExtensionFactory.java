package org.apache.camel.cdi.osgi;


import org.apache.camel.cdi.CdiCamelExtension;
import org.osgi.framework.Bundle;
import org.osgi.framework.PrototypeServiceFactory;
import org.osgi.framework.ServiceRegistration;

import javax.enterprise.inject.spi.Extension;

class CdiCamelExtensionFactory implements PrototypeServiceFactory<Extension> {

    @Override
    public Extension getService(Bundle bundle, ServiceRegistration<Extension> registration) {
        return new CdiCamelExtension();
    }

    @Override
    public void ungetService(Bundle bundle, ServiceRegistration<Extension> registration, Extension service) {
    }
}
