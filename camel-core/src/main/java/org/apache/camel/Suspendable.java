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
package org.apache.camel;

/**
 * Marker interface to indicate a custom component has custom implementation for suspending the {@link SuspendableService} service.
 * <br/>
 * This is needed to let Camel know if there is special code happening during a suspension.
 * <p/>
 * The {@link org.apache.camel.support.ServiceSupport} implementation that most Camel components / endpoints etc use
 * as base class is a {@link SuspendableService} but the actual implementation may not have special logic for suspend.
 * Therefore this marker interface is introduced to indicate when the implementation has special code for suspension.
 * <p/>
 * It is assumed that a service having a custom logic for suspension implements also a custom logic for resuming.
 *
 * @see SuspendableService
 */
public interface Suspendable {
}
