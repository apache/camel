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
package org.apache.camel.core.osgi;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.core.osgi.test.MyService;
import org.apache.camel.language.simple.SimpleLanguage;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LanguageResolver;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.springframework.osgi.mock.MockBundleContext;
import org.springframework.osgi.mock.MockServiceReference;

public class CamelMockBundleContext extends MockBundleContext {
    
    public static final String SERVICE_PID_PREFIX = "test.";

    public CamelMockBundleContext(Bundle bundle) {
        super(bundle);
    }

    public Object getService(@SuppressWarnings("rawtypes") ServiceReference reference) {
        String[] classNames = (String[]) reference.getProperty(Constants.OBJECTCLASS);        
        String classNames0 = classNames != null ? classNames[0] : null;
        String pid = (String)reference.getProperty(Constants.SERVICE_PID);
        if (classNames0 != null && classNames0.equals("org.apache.camel.core.osgi.test.MyService")) {
            return new MyService();
        } else if (pid != null && pid.equals(SERVICE_PID_PREFIX + "org.apache.camel.core.osgi.test.MyService")) {
            return new MyService();
        } else if (classNames0 != null && classNames0.equals(ComponentResolver.class.getName())) {
            return new ComponentResolver() {
                public Component resolveComponent(String name, CamelContext context) throws Exception {
                    if (name.equals("file_test")) {
                        return new FileComponent();
                    }
                    return null;
                }
            };
        } else if (classNames0 != null && classNames0.equals(LanguageResolver.class.getName())) {
            return new LanguageResolver() {
                public Language resolveLanguage(String name, CamelContext context) {
                    if (name.equals("simple")) {
                        return new SimpleLanguage();
                    }
                    return null;
                }
            };
        } else {
            return null;
        }    
    }

    public ServiceReference getServiceReference(String clazz) {
        // lookup Java class if clazz contains dot (.) symbol
        if (clazz.contains(".")) {
            try {
                final Class c = Class.forName(clazz);
                return super.getServiceReference(clazz);
            } catch (ClassNotFoundException ex) {
                return null; // class not found so no service reference is returned
            }
        } else {
            return super.getServiceReference(clazz);
        }
    }
    
    private static void addServicePID(ServiceReference[] srs, String filter) {
        for (ServiceReference sr : srs) {
            if (sr instanceof MockServiceReference) {
                Dictionary properties = new Hashtable();
                String pid = filter.replace("(" + Constants.SERVICE_PID + "=", "").replace(")", "");
                properties.put(Constants.SERVICE_PID, pid);
                for (String key : sr.getPropertyKeys()) {
                    if (properties.get(key) == null) {
                        properties.put(key, sr.getProperty(key));
                    }
                }
                ((MockServiceReference)sr).setProperties(properties);
            }
        }
    }
    
    @SuppressWarnings("rawtypes")
    public ServiceReference[] getServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
        // just simulate when the bundle context doesn't have right service reference
        if (filter != null && filter.indexOf("name=test") > 0) {
            return null;
        } else {
            ServiceReference[] srs = super.getServiceReferences(clazz, filter);
            
            // set service.pid property by filter
            if (filter != null && filter.indexOf(Constants.SERVICE_PID + "=") > 0) {
                addServicePID(srs, filter);
            }
            return srs;
        }
    }
   
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ServiceReference[] getAllServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
        // just simulate when the bundle context doesn't have right service reference
        if (filter != null && filter.indexOf("name=test") > 0) {
            return null;
        }
        MockServiceReference reference = new MockServiceReference(getBundle(), new String[] {clazz});
        // setup the name property with the class name
        Dictionary properties = new Hashtable();
        properties.put("name", clazz);
        reference.setProperties(properties);
        return new ServiceReference[] {reference};
    }
}
