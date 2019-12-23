/*
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
package org.apache.camel.component.cxf;

import java.lang.annotation.Annotation;

import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.ws.WebServiceProvider;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

public final class CxfEndpointUtils {
    private static final Logger LOG = LoggerFactory.getLogger(CxfEndpointUtils.class);

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
        QName answer = endpoint.getPortNameAsQName();
        if (answer == null) {
            String portLocalName = getCxfEndpointPropertyValue((CxfSpringEndpoint)endpoint, CxfConstants.PORT_LOCALNAME);
            String portNamespace = getCxfEndpointPropertyValue((CxfSpringEndpoint)endpoint, CxfConstants.PORT_NAMESPACE);
            if (portLocalName != null) {
                answer = new QName(portNamespace, portLocalName);
            }
        }
        return answer;
    }

    // only used by test currently
    public static QName getServiceName(final CxfEndpoint endpoint) {
        QName answer = endpoint.getServiceNameAsQName();
        if (answer == null) {
            String serviceLocalName = getCxfEndpointPropertyValue((CxfSpringEndpoint)endpoint, CxfConstants.SERVICE_LOCALNAME);
            String serviceNamespace = getCxfEndpointPropertyValue((CxfSpringEndpoint)endpoint, CxfConstants.SERVICE_NAMESPACE);
            if (serviceLocalName != null) {
                answer = new QName(serviceNamespace, serviceLocalName);
            }
        }
        return answer;
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
    
    // only used by test currently
    public static void checkServiceClassName(String className) throws CamelException {
        if (ObjectHelper.isEmpty(className)) {
            throw new CamelException("serviceClass is required for CXF endpoint configuration");
        }
    }
    
    // only used by test currently
    public static String getCxfEndpointPropertyValue(CxfSpringEndpoint endpoint, String property) {
        return (String)endpoint.getProperties().get(property);
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
            LOG.trace("Client address is overridden by header '{}' to value '{}'",
                Exchange.DESTINATION_OVERRIDE_URL, retval);
        }
        return retval;
    }
    
    /**
     * Create a CXF bus with either BusFactory or SpringBusFactory if Camel Context
     * is SpringCamelContext.  In the latter case, this method updates the bus 
     * configuration with the applicationContext which SpringCamelContext holds 
     * 
     * @param context - the Camel Context
     */
    public static Bus createBus(CamelContext context) {
        BusFactory busFactory = BusFactory.newInstance();

        if (context instanceof SpringCamelContext) {
            SpringCamelContext springCamelContext = (SpringCamelContext) context;
            ApplicationContext applicationContext = springCamelContext.getApplicationContext();
            busFactory = new SpringBusFactory(applicationContext);
        }
        return busFactory.createBus();
    }
}



