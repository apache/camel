package org.apache.camel.blueprint;

import org.apache.camel.impl.DefaultModelJAXBContextFactory;

/**
 * @author
 */
public class BlueprintModelJAXBContextFactory extends DefaultModelJAXBContextFactory{

    public static final String ADDITIONAL_JAXB_CONTEXT_PACKAGES = ":"
            + "org.apache.camel.core.xml:"
            + "org.apache.camel.blueprint:"
            + "org.apache.camel.util.blueprint:";

    protected String getPackages() {
        return super.getPackages() + ADDITIONAL_JAXB_CONTEXT_PACKAGES;
    }

    protected ClassLoader getClassLoader() {
        return getClass().getClassLoader();
    }
}