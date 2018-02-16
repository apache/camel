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
package org.apache.camel.component.javaspace;

import java.rmi.RMISecurityManager;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.lookup.entry.Name;
import net.jini.space.JavaSpace;

/**
 * @version 
 */
public final class JiniSpaceAccessor {

    private JiniSpaceAccessor() {
    }
    
    public static JavaSpace findSpace(String url, String spaceName) throws Exception {

        if (System.getSecurityManager() == null) {
            System.setSecurityManager(new RMISecurityManager());
        }

        Class<?>[] classes = new Class<?>[] {net.jini.space.JavaSpace.class};
        Name sn = new Name(spaceName);
        ServiceTemplate tmpl = new ServiceTemplate(null/* serviceID */, classes, new Entry[] {sn});

        LookupLocator locator = new LookupLocator(url); // <protocol>://<hostname>
        ServiceRegistrar sr = locator.getRegistrar();
        JavaSpace space = (JavaSpace) sr.lookup(tmpl);

        return space;
    }
}
