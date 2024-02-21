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
 * Factory for {@link VariableRepository}.
 */
public interface VariableRepositoryFactory {

    /**
     * Registry bean id for global {@link VariableRepository}.
     */
    String GLOBAL_VARIABLE_REPOSITORY_ID = "global-variable-repository";

    /**
     * Registry bean id for route {@link VariableRepository}.
     */
    String ROUTE_VARIABLE_REPOSITORY_ID = "route-variable-repository";

    /**
     * Gets the {@link VariableRepository} for the given id
     *
     * @param  id the repository id
     * @return    the repository or <tt>null</tt> if none found
     */
    VariableRepository getVariableRepository(String id);

}
