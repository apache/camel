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
package org.apache.camel.bam;

import javax.persistence.EntityManager;

/**
 * Function operating on the {@code EntityManager} instance. Intended to be executed as a part of the template method
 * defined by the {@link org.apache.camel.bam.EntityManagerTemplate#execute(EntityManagerCallback)}.
 * {@code EntityManager} should not be closed by the callback, as doing this is the responsibility of the
 * {@link org.apache.camel.bam.EntityManagerTemplate}.
 *
 * @param <T>
 */
public interface EntityManagerCallback<T> {

    T execute(EntityManager entityManager);

}
