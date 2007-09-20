package org.apache.camel.osgi;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

/**
 * Created by IntelliJ IDEA.
 * User: gnodet
 * Date: Sep 20, 2007
 * Time: 11:24:23 AM
 * To change this template use File | Settings | File Templates.
 */
public class CamelNamespaceHandler extends org.apache.camel.spring.handler.CamelNamespaceHandler {

    public void init() {
        super.init();
        registerParser("camelContext", new CamelContextBeanDefinitionParser(CamelContextFactoryBean.class));
    }

    protected JAXBContext createJaxbContext() throws JAXBException {
        return JAXBContext.newInstance("org.apache.camel.osgi:" + JAXB_PACKAGES);
    }

}
