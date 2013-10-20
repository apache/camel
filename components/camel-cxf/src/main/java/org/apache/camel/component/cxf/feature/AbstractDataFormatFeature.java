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

package org.apache.camel.component.cxf.feature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.cxf.endpoint.Client;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.interceptor.ClientFaultConverter;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptor;
import org.slf4j.Logger;

/**
 * The abstract class for the data format feature
 */
public abstract class AbstractDataFormatFeature extends AbstractFeature {
    protected static final Collection<Class<?>> REMOVING_FAULT_IN_INTERCEPTORS;
    
    static {
        REMOVING_FAULT_IN_INTERCEPTORS = new ArrayList<Class<?>>();
        REMOVING_FAULT_IN_INTERCEPTORS.add(ClientFaultConverter.class);
    }

    // The interceptors which need to be keeped
    protected Set<String> inInterceptorNames = new HashSet<String>();
    protected Set<String> outInterceptorNames = new HashSet<String>();
    protected abstract Logger getLogger();
    

    @Deprecated
    // It will be removed in Camel 3.0
    protected void removeInterceptorWhichIsInThePhases(List<Interceptor<? extends Message>> interceptors, String[] phaseNames) {
        removeInterceptorWhichIsInThePhases(interceptors, phaseNames, null);
    }
    
    protected void removeInterceptorWhichIsInThePhases(List<Interceptor<? extends Message>> interceptors, String[] phaseNames, Set<String> needToBeKept) {
        for (Interceptor<? extends Message> i : interceptors) {
            if (i instanceof PhaseInterceptor) {
                PhaseInterceptor<? extends Message> p = (PhaseInterceptor<? extends Message>) i;
                for (String phaseName : phaseNames) {
                    if (p.getPhase().equals(phaseName)) {
                        // To support the old API
                        if (needToBeKept == null) {
                            getLogger().info("removing the interceptor " + p);
                            interceptors.remove(p);
                            break;
                        } else if (!needToBeKept.contains(p.getClass().getName())) {
                            getLogger().info("removing the interceptor " + p);
                            interceptors.remove(p);
                            break; 
                        }
                    }
                }
            }
        }
    }
    
    @Deprecated
    // It will be removed in Camel 3.0
    protected void removeInterceptorWhichIsOutThePhases(List<Interceptor<? extends Message>> interceptors, String[] phaseNames) {
        removeInterceptorWhichIsOutThePhases(interceptors, phaseNames, null);
    }

    protected void removeInterceptorWhichIsOutThePhases(List<Interceptor<? extends Message>> interceptors, String[] phaseNames, Set<String> needToBeKept) {
        for (Interceptor<? extends Message> i : interceptors) {
            boolean outside = false;
            if (i instanceof PhaseInterceptor) {
                PhaseInterceptor<? extends Message> p = (PhaseInterceptor<? extends Message>) i;
                for (String phaseName : phaseNames) {
                    if (p.getPhase().equals(phaseName)) {
                        outside = true;
                        break;
                    }
                }
                if (!outside) {
                    // To support the old API
                    if (needToBeKept == null) {
                        getLogger().info("removing the interceptor " + p);
                        interceptors.remove(p);
                    } else if (!needToBeKept.contains(p.getClass().getName())) {
                        getLogger().info("removing the interceptor " + p);
                        interceptors.remove(p);
                    }
                }
            }
        }
    }

    protected void removeInterceptors(List<Interceptor<? extends Message>> interceptors, 
                                      Collection<Class<?>> toBeRemovedInterceptors) {
        for (Interceptor<? extends Message> interceptor : interceptors) {
            if (toBeRemovedInterceptors.contains(interceptor.getClass())) {
                getLogger().info("removing the interceptor " + interceptor);
                interceptors.remove(interceptor);
            }
        }
    }
    
    protected void removeInterceptor(List<Interceptor<? extends Message>> interceptors, 
                                     Class<? extends Interceptor<? extends Message>> cls) {
        for (Interceptor<? extends Message> interceptor : interceptors) {
            if (interceptor.getClass().equals(cls)) {
                interceptors.remove(interceptor);
            }
        }        
    }
    
    protected void removeFaultInInterceptorFromClient(Client client) {
        removeInterceptors(client.getInFaultInterceptors(), REMOVING_FAULT_IN_INTERCEPTORS);
        removeInterceptors(client.getEndpoint().getService().getInFaultInterceptors(), REMOVING_FAULT_IN_INTERCEPTORS);
        removeInterceptors(client.getEndpoint().getInFaultInterceptors(), REMOVING_FAULT_IN_INTERCEPTORS);
        removeInterceptors(client.getEndpoint().getBinding().getInFaultInterceptors(), REMOVING_FAULT_IN_INTERCEPTORS);
    }
    
    public void addInIntercepters(List<Interceptor<? extends Message>> interceptors) {
        for (Interceptor<? extends Message> interceptor : interceptors) {
            inInterceptorNames.add(interceptor.getClass().getName());
        }
    }
    
    public void addOutInterceptors(List<Interceptor<? extends Message>> interceptors) {
        for (Interceptor<? extends Message> interceptor : interceptors) {
            outInterceptorNames.add(interceptor.getClass().getName());
        }
    }
    
    public Set<String> getInInterceptorNames() {
        return inInterceptorNames;
    }
    
    public Set<String> getOutInterceptorNames() {
        return outInterceptorNames;
    }
}
