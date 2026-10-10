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
package org.apache.camel.semantic.internal;

import java.util.Set;

/** Automatic discovery found zero or multiple eligible experts. */
public final class NoUniqueSemanticExpertException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public NoUniqueSemanticExpertException(Set<String> experts) {
        super("Semantic language requires exactly one eligible expert; available experts: " + experts
              + ". Specify expert or configure camel.language.semantic.default-expert explicitly");
    }
}
