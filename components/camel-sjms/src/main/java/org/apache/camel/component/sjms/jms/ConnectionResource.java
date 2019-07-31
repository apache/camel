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
package org.apache.camel.component.sjms.jms;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;

/**
 * <p>
 * The ConnectionResource is the contract used to provide {@link Connection}
 * pools to the SJMS component. A user should use this to provide access to an
 * alternative pooled connection resource such as a {@link Connection} pool that
 * is managed by a J2EE container.
 * </p>
 * <p>
 * It is recommended though that for standard {@link ConnectionFactory}
 * providers you use the {@link ConnectionFactoryResource) implementation that
 * is provided with SJMS as it is optimized for this component.
 * </p>
 */
public interface ConnectionResource {

    /**
     * Borrows a {@link Connection} from the connection pool. An exception
     * should be thrown if no resource is available.
     *
     * @return {@link Connection}
     * @throws Exception when no resource is available
     */
    Connection borrowConnection() throws Exception;

    /**
     * Returns the {@link Connection} to the connection pool.
     *
     * @param connection the borrowed {@link Connection}
     * @throws Exception
     */
    void returnConnection(Connection connection) throws Exception;

}
