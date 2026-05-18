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
 * Thrown when an operation is attempted on a {@link Service} that has already been stopped and therefore cannot service
 * the call.
 * <p/>
 * Typically raised when a producer or template is invoked after the owning {@link CamelContext} (or its parent route)
 * has been shut down.
 */
public class AlreadyStoppedException extends RuntimeCamelException {

    public AlreadyStoppedException() {
        super("Already stopped");
    }
}
