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
package org.apache.camel.guice.support;

import java.lang.annotation.Annotation;

/**
 * There's no simple way to associate a Scope with an annotation which often
 * leads Scoping to return null. This interface allows Scope implementations to
 * expose its scope annotation
 * 
 * @version
 */
public interface HasScopeAnnotation {
    /**
     * Returns the scope annotation associated with this object
     */
    Class<? extends Annotation> getScopeAnnotation();
}
