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
package org.apache.camel.util;

import org.apache.camel.Service;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Collection;

/**
 * A collection of helper methods for working with {@link Service} objects
 *
 * @version $Revision$
 */
public class ServiceHelper {
    private static final transient Log log = LogFactory.getLog(ServiceHelper.class);


    /**
     * Starts all of the given services
     */
    public static void startServices(Object... services) throws Exception {
        for (Object value : services) {
            startService(value);
        }
    }

    /**
     * Starts all of the given services
     */
    public static void startServices(Collection services) throws Exception {
        for (Object value : services) {
            if (value instanceof Service) {
                Service service = (Service) value;
                service.start();
            }
        }
    }

    public static void startService(Object value) throws Exception {
        if (value instanceof Service) {
            Service service = (Service) value;
            service.start();
        }
        else if (value instanceof Collection) {
            startServices((Collection) value);
        }
    }

    /**
     * Stops all of the given services, throwing the first exception caught
     */
    public static void stopServices(Object... services) throws Exception {
        Exception firstException = null;
        for (Object value : services) {
            if (value instanceof Service) {
                Service service = (Service) value;
                try {
                    service.stop();
                }
                catch (Exception e) {
                    log.debug("Caught exception shutting down: " + e, e);
                    if (firstException == null) {
                        firstException = e;
                    }
                }
            }
        }
        if (firstException != null) {
            throw firstException;
        }
    }
    /**
     * Stops all of the given services, throwing the first exception caught
     */
    public static void stopServices(Collection services) throws Exception {
        Exception firstException = null;
        for (Object value : services) {
            if (value instanceof Service) {
                Service service = (Service) value;
                try {
                    service.stop();
                }
                catch (Exception e) {
                    log.debug("Caught exception shutting down: " + e, e);
                    if (firstException == null) {
                        firstException = e;
                    }
                }
            }
        }
        if (firstException != null) {
            throw firstException;
        }
    }
}
