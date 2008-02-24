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
package org.apache.camel.spi;

/**
 * Represents a service registry which may be implemented via a Spring ApplicationContext,
 * via JNDI, a simple Map or the OSGI Service Registry
 *
 * @version $Revision$
 */
public interface Registry {

    /**
     * Looks up a service in the registry, returning the service or null if it could not be found.
     *
     * @param name the name of the service
     * @param type the type of the required service
     * @return the service from the registry or null if it could not be found
     */
    <T> T lookup(String name, Class<T> type);

    /**
     * Looks up a service in the registry based purely on name,
     * returning the service or null if it could not be found.
     *
     * @param name the name of the service
     * @return the service from the registry or null if it could not be found
     */
    Object lookup(String name);
}
