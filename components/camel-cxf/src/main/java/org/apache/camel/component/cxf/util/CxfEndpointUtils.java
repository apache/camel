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

import java.net.URI;
import java.net.URL;
import java.util.logging.Logger;

import javax.jws.WebService;
import javax.xml.namespace.QName;

import org.apache.camel.CamelException;
import org.apache.camel.component.cxf.CxfEndpoint;
import org.apache.camel.component.cxf.CxfMessage;
import org.apache.camel.component.cxf.DataFormat;


import org.apache.cxf.Bus;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.i18n.Message;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.jaxws.JaxWsClientFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.cxf.jaxws.support.JaxWsServiceFactoryBean;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.factory.AbstractServiceFactoryBean;
import org.apache.cxf.service.factory.ReflectionServiceFactoryBean;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.wsdl11.WSDLServiceFactory;


public final class CxfEndpointUtils {    
    public static final String PROP_NAME_PORT = "port";
    public static final String PROP_NAME_SERVICE = "service";
    public static final String PROP_NAME_SERVICECLASS = "serviceClass";
    public static final String PROP_NAME_DATAFORMAT = "dataFormat";
    public static final String DATAFORMAT_POJO = "pojo";
    public static final String DATAFORMAT_MESSAGE = "message";
    public static final String DATAFORMAT_PAYLOAD = "payload";
    private static final Logger LOG = LogUtils.getL7dLogger(CxfEndpointUtils.class);
    
    private CxfEndpointUtils() {
        // not constructed
    }

    static QName getQName(final String name) {      
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
    
    public static QName getPortName(final CxfEndpoint endpoint) {
        return getQName(endpoint.getPortName());
    }
    
    public static QName getServiceName(final CxfEndpoint endpoint) {
        return getQName(endpoint.getServiceName());
    }

    public static EndpointInfo getEndpointInfo(final Service service, final CxfEndpoint endpoint) {
        EndpointInfo endpointInfo = null;
        final java.util.Collection<EndpointInfo> endpoints = service.getServiceInfos().get(0).getEndpoints();
        if (endpoints.size() == 1) {
            endpointInfo = endpoints.iterator().next();
        } else {
            final String port = endpoint.getPortName();
            if (port != null) {
                final QName endpointName = QName.valueOf(port);
                endpointInfo = service.getServiceInfos().get(0).getEndpoint(endpointName);
            }
            //TBD may be delegate to the EndpointUri params.  
        }

        return endpointInfo;
    }

    public static boolean hasWebServiceAnnotation(Class<?> cls) {
        if (cls == null) {
            return false;
        }
        if (null != cls.getAnnotation(WebService.class)) {
            return true;
        }
        for (Class<?> inf : cls.getInterfaces()) {
            if (null != inf.getAnnotation(WebService.class)) {
                return true;
            }
        }

        return hasWebServiceAnnotation(cls.getSuperclass());
    }
    
    public static ServerFactoryBean getServerFactoryBean(Class<?> cls) throws CamelException {
        
        try {            
            boolean isJSR181SEnabled = CxfEndpointUtils.hasWebServiceAnnotation(cls);
            ServerFactoryBean serverFactory = isJSR181SEnabled ? new JaxWsServerFactoryBean() 
                        : new ServerFactoryBean();            
            return serverFactory;
        } catch (Exception e) {
            throw new CamelException(e);
        }
        
    }
    
    public static ClientFactoryBean getClientFactoryBean(Class<?> cls) throws CamelException {
        try {            
            boolean isJSR181SEnabled = CxfEndpointUtils.hasWebServiceAnnotation(cls);
            ClientFactoryBean clientFactory = isJSR181SEnabled ? new JaxWsClientFactoryBean() 
                        : new ClientFactoryBean();            
            return clientFactory;
        } catch (Exception e) {
            throw new CamelException(e);
        }
    }
    
    //TODO check the CxfEndpoint information integration
    public static void checkEndpiontIntegration(CxfEndpoint endpoint, Bus bus) throws CamelException {

        String wsdlLocation = endpoint.getWsdlURL();
        QName serviceQName = CxfEndpointUtils.getQName(endpoint.getServiceName());
        String serviceClassName = endpoint.getServiceClass();
        DataFormat dataFormat = CxfEndpointUtils.getDataFormat(endpoint);
        URL wsdlUrl = null;
        if (wsdlLocation != null) {
            try {
                wsdlUrl = UriUtils.getWsdlUrl(new URI(wsdlLocation));
            } catch (Exception e) {
                throw new CamelException(e);
            }
        }
        if (serviceQName == null) {
            throw new CamelException(new Message("SVC_QNAME_NOT_FOUND_X", LOG, endpoint.getServiceName()).toString());
        }

        if (serviceClassName == null && dataFormat == DataFormat.POJO) {
            throw new CamelException(new Message("SVC_CLASS_PROP_IS_REQUIRED_X", LOG).toString());
        }
        AbstractServiceFactoryBean serviceFactory = null;
        try {

            if (serviceClassName != null) {
                Class<?> cls = ClassLoaderUtils.loadClass(serviceClassName, CxfEndpointUtils.class);

                boolean isJSR181SEnabled = CxfEndpointUtils.hasWebServiceAnnotation(cls);

                serviceFactory = isJSR181SEnabled
                    ? new JaxWsServiceFactoryBean() : new ReflectionServiceFactoryBean();
                serviceFactory.setBus(bus);
                if (wsdlUrl != null) {
                    ((ReflectionServiceFactoryBean)serviceFactory).setWsdlURL(wsdlUrl);
                }
                if (serviceQName != null) {
                    ((ReflectionServiceFactoryBean)serviceFactory).setServiceName(serviceQName);
                }    
                ((ReflectionServiceFactoryBean)serviceFactory).setServiceClass(cls);

            } else {
                if (wsdlUrl == null) {
                    throw new CamelException(new Message("SVC_WSDL_URL_IS_NULL_X", LOG, wsdlLocation).toString());
                }
                serviceFactory = new WSDLServiceFactory(bus, wsdlUrl, serviceQName);
            }

        } catch (ClassNotFoundException cnfe) {
            throw new CamelException(new Message("CLASS_X_NOT_FOUND ", LOG, serviceClassName).toString(), cnfe);
        } catch (Exception e) {
            throw new CamelException(e);
        }       
    }
        
    public static DataFormat getDataFormat(CxfEndpoint endpoint) throws CamelException {
        String dataFormatString = endpoint.getDataFormat();
        DataFormat retval = DataFormat.POJO; 
        
        if (dataFormatString != null) {
            try {
                retval = DataFormat.valueOf(dataFormatString.toUpperCase());
            } catch (IllegalArgumentException iae) {
                throw new CamelException(new Message("INVALID_MESSAGE_FORMAT_XXXX", LOG, dataFormatString).toString()
                                         , iae);
            }
        }
        return retval;
    }
}

