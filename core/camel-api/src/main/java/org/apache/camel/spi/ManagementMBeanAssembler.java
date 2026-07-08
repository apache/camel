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
package org.apache.camel.spi;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.modelmbean.ModelMBean;

import org.apache.camel.StaticService;
import org.jspecify.annotations.Nullable;

/**
 * Assembles a {@link javax.management.modelmbean.ModelMBean} from a Camel managed object so it can be registered in the
 * {@link javax.management.MBeanServer}.
 * <p/>
 * Camel's JMX layer does not require managed objects to implement a fixed MBean interface. Instead, managed facades
 * (such as {@code ManagedCamelContext} or {@code ManagedRoute}) are annotated with
 * {@code @org.apache.camel.api.management.ManagedResource}, {@code @ManagedAttribute}, and {@code @ManagedOperation}.
 * The assembler scans those annotations and builds a {@link javax.management.modelmbean.RequiredModelMBean} that
 * exposes exactly the declared attributes and operations.
 * <p/>
 * The {@link ManagementAgent} calls
 * {@link #assemble(javax.management.MBeanServer, Object, javax.management.ObjectName)} for each managed object before
 * registering it. Replacing this implementation allows custom annotation processors or alternative MBean descriptors to
 * be used.
 * <p/>
 * See <a href="https://camel.apache.org/manual/jmx.html">JMX</a> in the Camel user manual.
 *
 * @see ManagementAgent
 * @see ManagementObjectStrategy
 */
public interface ManagementMBeanAssembler extends StaticService {

    /**
     * Assemble the {@link javax.management.modelmbean.ModelMBean}.
     *
     * @param  mBeanServer the mbean server
     * @param  obj         the object
     * @param  name        the object name to use in JMX
     * @return             the assembled {@link javax.management.modelmbean.ModelMBean}, or <tt>null</tt> if not
     *                     possible to assemble an MBean
     * @throws JMException is thrown if error assembling the mbean
     */
    @Nullable
    ModelMBean assemble(MBeanServer mBeanServer, Object obj, ObjectName name) throws JMException;

}
