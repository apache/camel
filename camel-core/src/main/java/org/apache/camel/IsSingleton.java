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
package org.apache.camel;

/**
 * Used for defining if a given class is singleton or not.  If the class is a singleton, 
 * then a single instance will be shared (and hence should be treated as immutable and
 * be used in a thread-safe manner).
 * <p/>
 * This interface is not implemented as a marker interface (i.e., it's necessary to read  
 * {@link #isSingleton()} instead of <tt>instanceof(IsSingleton))</tt>.
 * This allows for subclasses to have a singleton status different from a parent and
 * for objects to have this value dynamically changed.
 * <p/>
 * Camel component is very often singleton based, only a few components is not.
 *
 * @version 
 */
public interface IsSingleton {

    /**
     * Whether this class supports being singleton or not.
     *
     * @return <tt>true</tt> to be a single shared instance, <tt>false</tt> to create new instances.
     */
    boolean isSingleton();

}
