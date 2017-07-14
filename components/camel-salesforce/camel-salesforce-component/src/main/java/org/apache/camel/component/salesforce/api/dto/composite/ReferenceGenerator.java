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
package org.apache.camel.component.salesforce.api.dto.composite;

/**
 * SObject tree Composite API interface for {@code referenceId} generation. For each object given to the
 * {@link ReferenceGenerator#nextReferenceFor(Object)} method, the implementation should generate reference identifiers.
 * Reference identifiers need to be unique within one SObject tree request and should start with alphanumeric character.
 * <p/>
 * For example you can provide your {@link ReferenceGenerator} implementation that uses identities within your own
 * system as references, i.e. primary keys of records in your database.
 *
 * @see Counter
 */
public interface ReferenceGenerator {

    /**
     * Generates unique, within a request, reference identifier for the given object. Reference identifier must start
     * with an alphanumeric.
     *
     * @param object
     *            object to generate reference identifier for
     * @return generated reference identifier
     */
    String nextReferenceFor(Object object);

}
