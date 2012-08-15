package org.apache.camel.web.connectors;

import java.util.List;

/**
 * 
 */
public interface CamelConnection {

    public CamelDataBean getCamelBean(String type, String name);

    public List<CamelDataBean> getCamelBeans(String type);

    public Object invokeOperation(String type, String name, String operationName, Object[] params, String[] signature);

}
