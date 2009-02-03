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
package org.apache.camel.component.cxf.jaxrs;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;

/**
 * A Endpoint class that represents a CXF JAX-RS endpoint.
 * 
 * @version $Revision$
 */
public class CxfJaxrsEndpoint extends DefaultEndpoint {

    private List<String> resourceClassnames;
    
    public CxfJaxrsEndpoint(String remaining, CxfJaxrsComponent component) {
        super(remaining, component);
    }

    public boolean isSingleton() {
        return true;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new CxfJaxrsConsumer(this, processor);
    }

    /**
     * Producer for Jaxrs endpoint is not supported.  This method will throw
     * {@link UnsupportedOperationException}.
     */
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Producer is not supported");
    }

    /**
     * @param resourceClassnames the resourceClassnames to set
     */
    public void setResourceClassnames(List<String> resourceClassnames) {
        this.resourceClassnames = resourceClassnames;
    }

    /**
     * @return the resourceClassnames
     */
    public List<String> getResourceClassnames() {
        return resourceClassnames;
    }

    /**
     * @return
     */
    public JAXRSServerFactoryBean createServerFactoryBean() throws Exception {
        JAXRSServerFactoryBean answer = new JAXRSServerFactoryBean();
        ObjectHelper.notNull(resourceClassnames, "resourceClassnames is not set");
        answer.setResourceClasses(loadClasses(resourceClassnames));
        answer.setAddress(getEndpointUri());
        return answer;
    }

    private List<Class> loadClasses(List<String> classNames) throws ClassNotFoundException {
        List<Class> answer = new ArrayList<Class>();
        for (String className : classNames) {
            answer.add(ClassLoaderUtils.loadClass(className, getClass()));
        }
        return answer;
    }

}
