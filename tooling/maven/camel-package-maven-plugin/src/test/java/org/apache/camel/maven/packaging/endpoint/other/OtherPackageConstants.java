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
package org.apache.camel.maven.packaging.endpoint.other;

import org.apache.camel.spi.Metadata;

public final class OtherPackageConstants {
    @Metadata(description = "key3 desc")
    public static final String KEY_3 = "name-3";
    @Metadata(description = "key4 desc")
    public static final String KEY_4 = "name-4";
    @Metadata(description = "key18 desc")
    public static final String KEY_18 = "name-18";

    private OtherPackageConstants() {

    }

    public final class Inner {
        @Metadata(description = "key5 desc")
        public static final String KEY_5 = "name-5";
        @Metadata(description = "key9 desc")
        public static final String KEY_9 = "name-9";

        private Inner() {
        }
    }

    public static class InnerStatic {
        @Metadata(description = "key6 desc")
        public static final String KEY_6 = "name-6";
        @Metadata(description = "key10 desc")
        public static final String KEY_10 = "name-10";
    }
}
