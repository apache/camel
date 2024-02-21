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

package org.apache.camel.test.infra.core;

import java.lang.annotation.Annotation;

final class ExtensionUtils {

    private ExtensionUtils() {

    }

    private static String commonFixtureMessage(Class<? extends Annotation> annotationClass, Object instance) {
        return "Unable to setup fixture " + annotationClass.getSimpleName() + " on " + instance.getClass().getName();
    }

    static String invocationTargetMessage(Class<? extends Annotation> annotationClass, Object instance, String methodName) {
        return commonFixtureMessage(annotationClass, instance) + " due to invocation target exception to method: " + methodName;
    }

    static String illegalAccessMessage(Class<? extends Annotation> annotationClass, Object instance, String methodName) {
        return commonFixtureMessage(annotationClass, instance) + " due to illegal access to method: " + methodName;
    }

    static String commonProviderMessage(Class<? extends Annotation> annotationClass, Class<?> clazz) {
        return "Unable to setup provider " + annotationClass.getSimpleName() + " on " + clazz;
    }
}
