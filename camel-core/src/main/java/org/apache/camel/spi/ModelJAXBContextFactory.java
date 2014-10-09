package org.apache.camel.spi;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

/**
 * Factory to abstract the creation of the Model's JAXBContext.
 *
 * @author
 */
public interface ModelJAXBContextFactory
{
    JAXBContext newJAXBContext() throws JAXBException;
}