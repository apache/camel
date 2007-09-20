package org.apache.camel.osgi;

import org.apache.camel.spring.SpringCamelContext;
import org.osgi.framework.BundleContext;
import org.springframework.osgi.context.BundleContextAware;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Created by IntelliJ IDEA.
 * User: gnodet
 * Date: Sep 20, 2007
 * Time: 10:31:52 AM
 * To change this template use File | Settings | File Templates.
 */
@XmlRootElement(name = "camelContext")
@XmlAccessorType(XmlAccessType.FIELD)
public class CamelContextFactoryBean extends org.apache.camel.spring.CamelContextFactoryBean implements BundleContextAware {

    @XmlTransient
    private BundleContext bundleContext;

    public BundleContext getBundleContext() {
        return bundleContext;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }


    protected SpringCamelContext createContext() {
        SpringCamelContext context = super.createContext();
        context.setComponentResolver(new OsgiComponentResolver(bundleContext));
        return context;
    }

}
