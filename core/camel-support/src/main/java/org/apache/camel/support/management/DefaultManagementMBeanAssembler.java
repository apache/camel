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
package org.apache.camel.support.management;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.modelmbean.InvalidTargetObjectTypeException;
import javax.management.modelmbean.ModelMBean;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.RequiredModelMBean;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedInstance;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.NotificationSenderAware;
import org.apache.camel.spi.ManagementMBeanAssembler;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An assembler to assemble a {@link javax.management.modelmbean.ModelMBean} which can be used
 * to register the object in JMX. The assembler is capable of using the Camel JMX annotations to
 * gather the list of JMX operations and attributes.
 */
public class DefaultManagementMBeanAssembler extends ServiceSupport implements ManagementMBeanAssembler {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultManagementMBeanAssembler.class);

    protected final MBeanInfoAssembler assembler;
    protected final CamelContext camelContext;

    public DefaultManagementMBeanAssembler(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.assembler = new MBeanInfoAssembler();
    }

    @Override
    public ModelMBean assemble(MBeanServer mBeanServer, Object obj, ObjectName name) throws JMException {
        ModelMBeanInfo mbi = null;
        ModelMBeanInfo standardMbi = null;
        Object custom = null;

        // prefer to use the managed instance if it has been annotated with JMX annotations
        if (obj instanceof ManagedInstance) {
            // there may be a custom embedded instance which have additional methods
            custom = ((ManagedInstance) obj).getInstance();
            if (custom != null && ObjectHelper.hasAnnotation(custom.getClass().getAnnotations(), ManagedResource.class)) {
                LOG.trace("Assembling MBeanInfo for: {} from custom @ManagedResource object: {}", name, custom);
                // get the mbean info into different groups (mbi = both, standard = standard out of the box mbi)
                mbi = assembler.getMBeanInfo(camelContext, obj, custom, name.toString());
                standardMbi = assembler.getMBeanInfo(camelContext, obj, null, name.toString());
            }
        }

        if (mbi == null) {
            // use the default provided mbean which has been annotated with JMX annotations
            LOG.trace("Assembling MBeanInfo for: {} from @ManagedResource object: {}", name, obj);
            mbi = assembler.getMBeanInfo(camelContext, obj, null, name.toString());
        }

        if (mbi == null) {
            return null;
        }

        RequiredModelMBean mbean;
        RequiredModelMBean mixinMBean = null;

        boolean sanitize = camelContext.getManagementStrategy().getManagementAgent().getMask() != null && camelContext.getManagementStrategy().getManagementAgent().getMask();

        // if we have a custom mbean then create a mixin mbean for the standard mbean which we would
        // otherwise have created that contains the out of the box attributes and operations
        // as we want a combined mbean that has both the custom and the standard
        if (standardMbi != null) {
            mixinMBean = (RequiredModelMBean) mBeanServer.instantiate(RequiredModelMBean.class.getName());
            mixinMBean.setModelMBeanInfo(standardMbi);
            try {
                mixinMBean.setManagedResource(obj, "ObjectReference");
            } catch (InvalidTargetObjectTypeException e) {
                throw new JMException(e.getMessage());
            }
            // use custom as the object to call
            obj = custom;
        }

        // use a mixin mbean model to combine the custom and standard (custom is optional)
        mbean = new MixinRequiredModelMBean(mbi, sanitize, standardMbi, mixinMBean);

        try {
            mbean.setManagedResource(obj, "ObjectReference");
        } catch (InvalidTargetObjectTypeException e) {
            throw new JMException(e.getMessage());
        }

        // Allows the managed object to send notifications
        if (obj instanceof NotificationSenderAware) {
            ((NotificationSenderAware)obj).setNotificationSender(new NotificationSenderAdapter(mbean));
        }

        return mbean;
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(assembler);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(assembler);
    }
}
