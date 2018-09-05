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

import java.util.Map;

import org.apache.camel.component.extension.ComponentVerifierExtension;

/**
 * Defines the interface used for validating component/endpoint parameters. The central method of this
 * interface is {@link #verify(Scope, Map)} which takes a scope and a set of parameters which should be verified.
 * <p/>
 * The return value is a {@link Result} of the verification
 *
 * @deprecated use {@link ComponentVerifierExtension}
 */
@Deprecated
public interface ComponentVerifier extends ComponentVerifierExtension {
}
