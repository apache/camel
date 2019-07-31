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
 * {@link org.apache.camel.Endpoint} can optionally implement this interface to
 * indicate whether or not it supports multiple consumers.
 * <p/>
 * By default endpoints are assumed <b>not</b> to support multiple consumers.
 * <p/>
 * A rare few endpoints do in fact support multiple consumers and thus the purpose of this interface.
 * For example JMS endpoints which have topics that can be consumed by multiple consumers.
 * <p/>
 * The purpose of this is to check on startup that we do not have multiple consumers
 * for the <b>same</b> endpoints. This prevents starting up with copy/paste mistakes in the Camel routes.
 */
public interface MultipleConsumersSupport {

    /**
     * Are multiple consumers supported?
     *
     * @return <tt>true</tt> if multiple consumers are supported
     */
    boolean isMultipleConsumersSupported();
}
