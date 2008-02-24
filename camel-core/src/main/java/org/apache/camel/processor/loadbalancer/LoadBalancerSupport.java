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
package org.apache.camel.processor.loadbalancer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Processor;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.util.ServiceHelper;

/**
 * A default base class for a {@link LoadBalancer} implementation
 *
 * @version $Revision$
 */
public abstract class LoadBalancerSupport extends ServiceSupport implements LoadBalancer {
    private List<Processor> processors = new CopyOnWriteArrayList<Processor>();

    public void addProcessor(Processor processor) {
        processors.add(processor);
    }

    public void removeProcessor(Processor processor) {
        processors.remove(processor);
    }

    public List<Processor> getProcessors() {
        return processors;
    }
    
    protected void doStart() throws Exception {
        ServiceHelper.startServices(processors);        
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(processors);       
    }
}
