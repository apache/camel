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
package org.apache.camel.blueprint;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import org.apache.karaf.bundle.core.BundleState;
import org.apache.karaf.bundle.core.BundleStateService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * A service for Karaf to get extended Bundle information related to Camel Context(s) declared in Blueprint
 * container.
 */
public class KarafBundleStateService implements BundleStateService {

    BlueprintCamelStateService camelStateService;

    public KarafBundleStateService(BlueprintCamelStateService camelStateService) {
        this.camelStateService = camelStateService;
    }

    @Override
    public String getName() {
        return "Camel Blueprint";
    }

    @Override
    public String getDiag(Bundle bundle) {
        if (getState(bundle) == BundleState.Failure) {
            // return stacktraces for failed camel contexts
            Map<String, Throwable> exceptions = camelStateService.getExceptions(bundle);
            StringWriter sw = new StringWriter();
            for (String contextId : exceptions.keySet()) {
                sw.append("Camel context \"").append(contextId).append("\"\n");
                Throwable t = exceptions.get(contextId);
                if (t instanceof NullPointerException) {
                    sw.append("Exception: NullPointerException\n");
                } else if (t.getMessage() != null) {
                    sw.append("Exception: ").append(t.getMessage()).append("\n");
                }
                t.printStackTrace(new PrintWriter(sw));
                sw.append("\n");
            }
            return sw.toString();
        }
        return null;
    }

    @Override
    public BundleState getState(Bundle bundle) {
        BundleState effective = BundleState.Unknown;
        for (BlueprintCamelStateService.State s : camelStateService.getStates(bundle)) {
            if (effective == BundleState.Unknown || s == BlueprintCamelStateService.State.Failure) {
                switch (s) {
                case Starting:
                    effective = BundleState.Starting;
                    break;
                case Active:
                    effective = BundleState.Active;
                    break;
                case Failure:
                    effective = BundleState.Failure;
                    break;
                default:
                    break;
                }
            }
        }
        return effective;
    }

    public ServiceRegistration<?> register(BundleContext context) {
        return context.registerService(BundleStateService.class, this, null);
    }

}
