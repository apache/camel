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
package org.apache.camel;

/**
 * An interface that allows safe copy (deep clone) of property value object when creating copy of Exchange objects.
 * Classes implementing this interface can be set as key value pair on exchange object via
 * {@link ExchangeExtension#setSafeCopyProperty(String, SafeCopyProperty)}.
 *
 * When exchange object is copied it will invoke {@link SafeCopyProperty#safeCopy()} method on properties set using
 * {@link ExchangeExtension#setSafeCopyProperty(String, SafeCopyProperty)}. This allows the property value object to
 * return a copy object to be set on the target exchange object instead of the original value object. This protects the
 * properties from unintended mutation when using parallelProcessing in Multicast or RecipientList EIP
 */
public interface SafeCopyProperty {

    /**
     * Implementations should implement this method to return a deep copy of the object which can be mutated
     * independently of the original object.
     *
     * @return copy object usually of the same class type which implements this interface
     */
    SafeCopyProperty safeCopy();

}
