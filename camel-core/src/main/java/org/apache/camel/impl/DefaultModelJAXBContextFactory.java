package org.apache.camel.impl;

import org.apache.camel.model.Constants;
import org.apache.camel.spi.ModelJAXBContextFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

/**
 * @author
 */
public class DefaultModelJAXBContextFactory implements ModelJAXBContextFactory {

    public JAXBContext newJAXBContext() throws JAXBException {
        return JAXBContext.newInstance(getPackages(), getClassLoader());
    }

    protected String getPackages() {
        return Constants.JAXB_CONTEXT_PACKAGES;
    }

    protected ClassLoader getClassLoader() {
        return getClass().getClassLoader();
    }
}