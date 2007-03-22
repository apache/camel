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
package org.apache.camel.component.jbi;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.jbi.JBIException;
import javax.jbi.component.Bootstrap;
import javax.jbi.component.InstallationContext;
import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * Base class for components bootstrap.
 * @version $Revision: 426415 $
 */
public class CamelContainerBootstrap implements Bootstrap {

    protected final transient Log logger = LogFactory.getLog(getClass());
    
    protected InstallationContext context;
    protected ObjectName mbeanName;
    
    public CamelContainerBootstrap() {
    }
    
    public ObjectName getExtensionMBeanName() {
        return mbeanName;
    }

    protected Object getExtensionMBean() throws Exception {
        return null;
    }
    
    protected ObjectName createExtensionMBeanName() throws Exception {
        return this.context.getContext().getMBeanNames().createCustomComponentMBeanName("bootstrap");
    }

    /* (non-Javadoc)
     * @see javax.jbi.component.Bootstrap#init(javax.jbi.component.InstallationContext)
     */
    public void init(InstallationContext installContext) throws JBIException {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Initializing bootstrap");
            }
            this.context = installContext;
            doInit();
            if (logger.isDebugEnabled()) {
                logger.debug("Bootstrap initialized");
            }
        } catch (JBIException e) {
            throw e;
        } catch (Exception e) {
            throw new JBIException("Error calling init", e);
        }
    }

    protected void doInit() throws Exception {
        Object mbean = getExtensionMBean();
        if (mbean != null) {
            this.mbeanName = createExtensionMBeanName();
            MBeanServer server = this.context.getContext().getMBeanServer();
            if (server == null) {
                throw new JBIException("null mBeanServer");
            }
            if (server.isRegistered(this.mbeanName)) {
                server.unregisterMBean(this.mbeanName);
            }
            server.registerMBean(mbean, this.mbeanName);
        }
    }
    
    /* (non-Javadoc)
     * @see javax.jbi.component.Bootstrap#cleanUp()
     */
    public void cleanUp() throws JBIException {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Cleaning up bootstrap");
            }
            doCleanUp();
            if (logger.isDebugEnabled()) {
                logger.debug("Bootstrap cleaned up");
            }
        } catch (JBIException e) {
            throw e;
        } catch (Exception e) {
            throw new JBIException("Error calling cleanUp", e);
        }
    }

    protected void doCleanUp() throws Exception {
        if (this.mbeanName != null) {
            MBeanServer server = this.context.getContext().getMBeanServer();
            if (server == null) {
                throw new JBIException("null mBeanServer");
            }
            if (server.isRegistered(this.mbeanName)) {
                server.unregisterMBean(this.mbeanName);
            }
        }
    }

    /* (non-Javadoc)
     * @see javax.jbi.component.Bootstrap#onInstall()
     */
    public void onInstall() throws JBIException {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Bootstrap onInstall");
            }
            doOnInstall();
            if (logger.isDebugEnabled()) {
                logger.debug("Bootstrap onInstall done");
            }
        } catch (JBIException e) {
            throw e;
        } catch (Exception e) {
            throw new JBIException("Error calling onInstall", e);
        }
    }

    protected void doOnInstall() throws Exception {
    }
    
    /* (non-Javadoc)
     * @see javax.jbi.component.Bootstrap#onUninstall()
     */
    public void onUninstall() throws JBIException {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Bootstrap onUninstall");
            }
            doOnUninstall();
            if (logger.isDebugEnabled()) {
                logger.debug("Bootstrap onUninstall done");
            }
        } catch (JBIException e) {
            throw e;
        } catch (Exception e) {
            throw new JBIException("Error calling onUninstall", e);
        }
    }

    protected void doOnUninstall() throws Exception {
    }
    
}
