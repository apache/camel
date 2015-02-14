package org.apache.camel.spring;

import org.apache.camel.impl.DefaultModelJAXBContextFactory;

/**
 * @author
 */
public class SpringModelJAXBContextFactory extends DefaultModelJAXBContextFactory {

    public static final String ADDITIONAL_JAXB_CONTEXT_PACKAGES = ":"
            + "org.apache.camel.core.xml:"
            + "org.apache.camel.spring:"
            + "org.apache.camel.util.spring:";

    protected ClassLoader getClassLoader() {
        return getClass().getClassLoader();
    }

    protected String getPackages() {
        return super.getPackages() + ADDITIONAL_JAXB_CONTEXT_PACKAGES;
    }
}
