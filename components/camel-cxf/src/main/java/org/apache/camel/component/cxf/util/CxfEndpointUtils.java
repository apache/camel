/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.cxf.util;

import java.lang.annotation.Annotation;

import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.ws.WebServiceProvider;

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.component.cxf.CxfConstants;
import org.apache.camel.component.cxf.CxfEndpoint;
import org.apache.camel.component.cxf.CxfSpringEndpoint;
import org.apache.camel.component.cxf.spring.CxfEndpointBean;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;

public final class CxfEndpointUtils {
    private static final Log LOG = LogFactory.getLog(CxfEndpointUtils.class);

    private CxfEndpointUtils() {
        // not constructed
    }

    public static QName getQName(final String name) {
        QName qName = null;
        if (name != null) {
            try {
                qName =  QName.valueOf(name);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return qName;
    }

    // only used by test currently
    public static QName getPortName(final CxfEndpoint endpoint) {
        if (endpoint.getPortName() != null) {
            return getQName(endpoint.getPortName());
        } else {
            String portLocalName = getCxfEndpointPropertyValue((CxfSpringEndpoint)endpoint, CxfConstants.PORT_LOCALNAME);
            String portNamespace = getCxfEndpointPropertyValue((CxfSpringEndpoint)endpoint, CxfConstants.PORT_NAMESPACE);
            if (portLocalName != null) {
                return new QName(portNamespace, portLocalName);
            } else {
                return null;
            }           
        }
    }

    // only used by test currently
    public static QName getServiceName(final CxfEndpoint endpoint) {
        if (endpoint.getServiceName() != null) {
            return getQName(endpoint.getServiceName());
        } else {
            String serviceLocalName = getCxfEndpointPropertyValue((CxfSpringEndpoint)endpoint, CxfConstants.SERVICE_LOCALNAME);
            String serviceNamespace = getCxfEndpointPropertyValue((CxfSpringEndpoint)endpoint, CxfConstants.SERVICE_NAMESPACE);
            if (serviceLocalName != null) {
                return new QName(serviceNamespace, serviceLocalName);
            } else {
                return null;
            }
        }
    }

    public static boolean hasWebServiceAnnotation(Class<?> cls) {
        return hasAnnotation(cls, WebService.class) || hasAnnotation(cls, WebServiceProvider.class);
    }

    public static boolean hasAnnotation(Class<?> cls, Class<? extends Annotation> annotation) {
        if (cls == null || cls == Object.class) {
            return false;
        }

        if (null != cls.getAnnotation(annotation)) {
            return true;
        }

        for (Class<?> interfaceClass : cls.getInterfaces()) {
            if (null != interfaceClass.getAnnotation(annotation)) {
                return true;
            }
        }
        return hasAnnotation(cls.getSuperclass(), annotation);
    }

    public static ServerFactoryBean getServerFactoryBean(Class<?> cls) throws CamelException {
        ServerFactoryBean serverFactory  = null;
        try {
            if (cls == null) {
                serverFactory = new ServerFactoryBean();
                serverFactory.setServiceFactory(new WSDLSoapServiceFactoryBean());

            } else {
                boolean isJSR181SEnabled = CxfEndpointUtils.hasWebServiceAnnotation(cls);
                serverFactory = isJSR181SEnabled ? new JaxWsServerFactoryBean()
                            : new ServerFactoryBean();
            }
            return serverFactory;
        } catch (Exception e) {
            throw new CamelException(e);
        }

    }

    public static ClientProxyFactoryBean getClientFactoryBean(Class<?> cls) throws CamelException {
        ClientProxyFactoryBean clientFactory = null;
        try {
            if (cls == null) {
                clientFactory = new ClientProxyFactoryBean();
                clientFactory.setServiceFactory(new WSDLSoapServiceFactoryBean());
            } else {
                boolean isJSR181SEnabled = CxfEndpointUtils.hasWebServiceAnnotation(cls);
                clientFactory = isJSR181SEnabled ? new JaxWsProxyFactoryBean()
                        : new ClientProxyFactoryBean();
            }
            return clientFactory;
        } catch (Exception e) {
            throw new CamelException(e);
        }
    }
    
    // only used by test currently
    public static void checkServiceClassName(String className) throws CamelException {
        if (ObjectHelper.isEmpty(className)) {
            throw new CamelException("serviceClass is required for CXF endpoint configuration");
        }
    }
    
    // only used by test currently
    public static String getCxfEndpointPropertyValue(CxfSpringEndpoint endpoint, String property) {
        String result = null;
        CxfEndpointBean cxfEndpointBean = endpoint.getBean();
        if (cxfEndpointBean != null && cxfEndpointBean.getProperties() != null) {
            result = (String) cxfEndpointBean.getProperties().get(property);
        }
        return result;
    }

    /**
     * Get effective address for a client to invoke a service.  It first looks for the 
     * {@link org.apache.camel.Exchange#DESTINATION_OVERRIDE_URL} in the IN message header.
     * If the header is not found, it will return the default address.
     * 
     * @param exchange
     * @param defaultAddress
     */
    public static String getEffectiveAddress(Exchange exchange, String defaultAddress) {
        String retval = exchange.getIn().getHeader(Exchange.DESTINATION_OVERRIDE_URL, String.class);
        if (retval == null) {
            retval = defaultAddress;
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Client address is overridden by header '" + Exchange.DESTINATION_OVERRIDE_URL
                          + "' to value '" + retval + "'");
            }
        }
        return retval;
    }
}



