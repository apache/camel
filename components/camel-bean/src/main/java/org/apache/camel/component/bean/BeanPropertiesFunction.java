package org.apache.camel.component.bean;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.ObjectHelper;

@org.apache.camel.spi.annotations.PropertiesFunction("bean")
public class BeanPropertiesFunction implements PropertiesFunction, CamelContextAware {
    private CamelContext camelContext;

    @Override
    public String getName() {
        return "bean";
    }

    @Override
    public String apply(String remainder) {
        String[] beanNameAndMethodName = remainder.split("\\.");
        String beanName = beanNameAndMethodName[0];
        String methodName = beanNameAndMethodName[1];

        Registry registry = getCamelContext().getRegistry();
        Object bean = registry.lookupByName(beanName);
        if (bean == null) {
            throw new NoSuchBeanException(beanName);
        }

        String answer = "";
        try {
            answer += (String) ObjectHelper.invokeMethodSafe(methodName, bean);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
        return answer;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

}
