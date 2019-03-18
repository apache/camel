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
package org.apache.camel.component.jpa;

/**
 * JPA constants
 */
public final class JpaConstants {

    public static final String ENTITY_MANAGER = "CamelEntityManager";
    
    public static final String JPA_PARAMETERS_HEADER = "CamelJpaParameters";

    /**
     * @deprecated use {@link #ENTITY_MANAGER}
     */
    @Deprecated
    public static final String ENTITYMANAGER = ENTITY_MANAGER;

    private JpaConstants() {
        // utility class
    }

}
