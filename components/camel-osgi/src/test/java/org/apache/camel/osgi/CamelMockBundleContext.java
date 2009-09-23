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
package org.apache.camel.osgi;

import org.apache.camel.osgi.test.MyService;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.springframework.osgi.mock.MockBundleContext;

public class CamelMockBundleContext extends MockBundleContext {
    
    public Object getService(ServiceReference reference) {        
        String[] classNames = (String[]) reference.getProperty(Constants.OBJECTCLASS);
        System.out.println("The class name is " + classNames[0]);
        if (classNames[0].equals("org.apache.camel.osgi.test.MyService")) {
            return new MyService();
        } else {
            return null;
        }    
    }

}
