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

import org.apache.camel.RuntimeCamelException;

/**
 * Represents a {@link BeanRepository} which may also be capable
 * of binding beans to its repository.
 */
public interface Registry extends BeanRepository {

    /**
     * Binds the bean to the repository (if possible).
     *
     * @param id   the id of the bean
     * @param bean the bean
     * @throws RuntimeCamelException is thrown if binding is not possible
     */
    void bind(String id, Object bean) throws RuntimeCamelException;
}
