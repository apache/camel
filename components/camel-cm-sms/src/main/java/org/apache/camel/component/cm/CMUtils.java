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
package org.apache.camel.component.cm;

public final class CMUtils {

    private CMUtils() {
    }

    public static boolean isGsm0338Encodeable(final String message) {
        return message.matches(CMConstants.GSM_0338_REGEX);
    }

    // TODO: Have a look at
    // https:// github.com/apache/camel/blob/master/components/camel-smpp/src/main/java/org/apache/camel/component/smpp/SmppUtils.java
}
