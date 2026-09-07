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
 * SPI for components and beans that capture a secret when they are configured, and that therefore need to be told when
 * that secret has been rotated so they can re-authenticate in place.
 * <p/>
 * When a vault component detects that a secret changed it triggers a {@link ContextReloadStrategy}, which reloads the
 * property placeholders and then all routes. Routes and endpoints are rebuilt from scratch, but components and beans in
 * the {@link Registry} are not, so anything holding a live authenticated resource - a pooled JMS connection factory, a
 * JDBC connection pool, a shared HTTP client - keeps using the credentials it captured at startup.
 * <p/>
 * Implement this interface on a {@link org.apache.camel.Component}, or register a bean implementing it in the
 * {@link Registry}, to be notified when this happens. The callback runs after the property placeholders have been
 * reloaded and the component options re-applied, but before the routes are restarted, so that by the time the routes
 * come back up the underlying resource is already authenticated with the new secret.
 * <p/>
 * Implementations should re-establish the authenticated resource rather than assume the process will be restarted, and
 * should be quick: the callback runs inline on the reload, and every implementation is notified before the routes come
 * back. A callback that throws is logged and ignored, so that one component cannot prevent the others from being
 * refreshed, nor break the reload as a whole.
 *
 * @see   ContextReloadStrategy
 * @since 4.23
 */
@FunctionalInterface
public interface SecretRotationAware {

    /**
     * Callback invoked when the secrets this component or bean captured may have been rotated, and it should
     * re-authenticate in place.
     * <p/>
     * The callback is advisory: it says that a reload was triggered, not which secrets changed. Implementations should
     * re-read their configuration and refresh the authenticated resource if the credentials it holds are no longer the
     * configured ones.
     *
     * @param  source    the source that triggered the reload, such as the vault task that detected the change
     * @throws Exception is thrown if the resource could not be re-authenticated
     */
    void onSecretRotation(Object source) throws Exception;

}
