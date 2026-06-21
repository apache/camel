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
package org.apache.camel.spi;

/**
 * Marker for an object whose id can be injected, such as EIP {@link org.apache.camel.Processor}s created from Camel
 * routes.
 * <p/>
 * Camel assigns the id (explicit or auto-generated) so it is available at runtime, which makes it easier to map the
 * object back to its corresponding model definition. Extends {@link HasId} to add the ability to set the id.
 *
 * @see HasId
 */
public interface IdAware extends HasId {

    /**
     * Sets the id
     *
     * @param id the id
     */
    void setId(String id);

    /**
     * Sets the id which has been auto generated
     *
     * @param id the auto generated id
     */
    default void setGeneratedId(String id) {
        setId(id);
    }

}
