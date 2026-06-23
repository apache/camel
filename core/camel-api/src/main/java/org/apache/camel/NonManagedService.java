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
 * Marker interface that excludes a {@link Service} from JMX management registration.
 * <p/>
 * By default, Camel registers internal services (components, producers, consumers, etc.) as JMX MBeans so that they can
 * be monitored and controlled at runtime. Services that implement {@code NonManagedService} are silently skipped during
 * this registration phase, which is appropriate for lightweight helper services, anonymous inner-class processors, or
 * services that would expose no useful management attributes.
 *
 * @see Service
 */
public interface NonManagedService {
}
