package org.apache.camel.web.management;

import javax.management.*;
import java.io.IOException;
import java.util.List;

/**
 *
 */
public interface CamelConnection {

    public CamelManagedBean getCamelBean(String type, String name);

    public List<CamelManagedBean> getCamelBeans(String type);

    public Object invokeOperation(String type, String name, String operationName, Object[] params, String[] signature);

}
