/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.management;

import java.util.HashSet;
import java.util.Set;

import javax.management.InstanceAlreadyExistsException;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.modelmbean.InvalidTargetObjectTypeException;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.RequiredModelMBean;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.InstrumentationAgent;
import org.apache.camel.impl.DefaultCamelContext;
import org.springframework.jmx.export.annotation.AnnotationJmxAttributeSource;
import org.springframework.jmx.export.assembler.MetadataMBeanInfoAssembler;

public class InstrumentationAgentImpl implements InstrumentationAgent, CamelContextAware {

	private MBeanServer server;
	private CamelContext context;
    private Set<ObjectName> mbeans = new HashSet<ObjectName>();
    MetadataMBeanInfoAssembler assembler;
	
    public InstrumentationAgentImpl() {
    	assembler = new MetadataMBeanInfoAssembler();
    	assembler.setAttributeSource(new AnnotationJmxAttributeSource());
    }
	public CamelContext getCamelContext() {
		return context;
	}

	public void setCamelContext(CamelContext camelContext) {
		context = camelContext;
	}

	public void setMBeanServer(MBeanServer server) {
		this.server = server;
	}
	
	public MBeanServer getMBeanServer() {
		return server;
	}

	public void register(Object obj, ObjectName name) throws JMException {
		register(obj, name, false);
	}

	public void register(Object obj, ObjectName name, boolean forceRegistration) throws JMException {
        try {
            registerMBeanWithServer(obj, name, forceRegistration);           
        } catch (NotCompliantMBeanException e) {        
            //If this is not a "normal" MBean, then try to deploy it using JMX annotations
        	ModelMBeanInfo mbi = null;
        	mbi = assembler.getMBeanInfo(obj, name.toString());
            RequiredModelMBean mbean = (RequiredModelMBean)server.instantiate(RequiredModelMBean.class.getName());
            mbean.setModelMBeanInfo(mbi);
            try {
            	mbean.setManagedResource(obj, "ObjectReference");
            } catch (InvalidTargetObjectTypeException itotex) {
                throw new JMException(itotex.getMessage());
            }
            registerMBeanWithServer(mbean, name, forceRegistration);
        }                
	}

	public void unregister(ObjectName name) throws JMException {
	}

	public void start() {
		if (context == null) {
			// LOG warning
			return;
		}
		
		if (context instanceof DefaultCamelContext) {
			DefaultCamelContext dc = (DefaultCamelContext)context;
			InstrumentationLifecycleStrategy ls = new InstrumentationLifecycleStrategy(this); 
			dc.setLifecycleStrategy(ls);
			ls.onContextCreate(context);
		}
	}
	
    public void stop() {
        //Using the array to hold the busMBeans to avoid the CurrentModificationException
        Object[] mBeans = mbeans.toArray();
        for (Object name : mBeans) {
        	mbeans.remove((ObjectName)name);
            try {
                unregister((ObjectName)name);
            } catch (JMException jmex) {
                // log
            }
        }
    }
    
    private void registerMBeanWithServer(Object obj, ObjectName name, boolean forceRegistration) 
    		throws JMException {
    	
	    ObjectInstance instance = null;
	    try {
	        instance = server.registerMBean(obj, name);           
	    } catch (InstanceAlreadyExistsException e) {            
	        if (forceRegistration) {
	        	server.unregisterMBean(name);               
	            instance = server.registerMBean(obj, name);
	        } else {
	            throw e;
	        }
	    }
	    
	    if (instance != null) {
	    	mbeans.add(name);
	    }
    }
}
