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
package org.apache.camel.component.jms;

import org.apache.camel.CamelContext;
import org.apache.camel.util.ObjectHelper;

/**
 * @version $Revision$
 */
public final class JmsHelper {

    private static final String DEFAULT_QUEUE_BROWSE_STRATEGY = "org.apache.camel.component.jms.DefaultQueueBrowseStrategy";

    private JmsHelper() {
        // utility class
    }

    /**
     * Is the spring version 2.0.x?
     *
     * @return <tt>true</tt> if 2.0.x or <tt>false</tt> if newer such as 2.5.x
     */
    public static boolean isSpring20x(CamelContext context) {
        // this class is only possible to instantiate in 2.5.x or newer
        Class<?> type = null;
        if (context != null) {
            type = context.getClassResolver().resolveClass(DEFAULT_QUEUE_BROWSE_STRATEGY, JmsComponent.class.getClassLoader());
        } else {
            type = ObjectHelper.loadClass(DEFAULT_QUEUE_BROWSE_STRATEGY, JmsComponent.class.getClassLoader());
        }
        
        if (type != null) {
            try {
                ObjectHelper.newInstance(type);
                return false;
            } catch (NoClassDefFoundError e) {
                return true;
            }
        } else {
            return true;
        }
    }
}
