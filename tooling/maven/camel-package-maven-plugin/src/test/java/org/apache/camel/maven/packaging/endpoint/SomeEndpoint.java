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
package org.apache.camel.maven.packaging.endpoint;

import org.apache.camel.maven.packaging.endpoint.other.OtherPackageConstants;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriPath;

import static org.apache.camel.maven.packaging.endpoint.SamePackageConstants.Inner.KEY_7;
import static org.apache.camel.maven.packaging.endpoint.SamePackageConstants.InnerStatic.KEY_8;
import static org.apache.camel.maven.packaging.endpoint.SomeEndpoint.Inner.KEY_13;
import static org.apache.camel.maven.packaging.endpoint.SomeEndpoint.InnerStatic.KEY_14;
import static org.apache.camel.maven.packaging.endpoint.other.OtherPackageConstants.KEY_4;
import static org.apache.camel.maven.packaging.endpoint.other.OtherPackageConstants.Inner.KEY_9;
import static org.apache.camel.maven.packaging.endpoint.other.OtherPackageConstants.InnerStatic.KEY_10;

@Metadata(
          headers = {
                  SomeEndpoint.KEY_1,
                  SamePackageConstants.KEY_2,
                  OtherPackageConstants.KEY_3,
                  KEY_4,
                  OtherPackageConstants.Inner.KEY_5,
                  OtherPackageConstants.InnerStatic.KEY_6,
                  KEY_7,
                  KEY_8,
                  KEY_9,
                  KEY_10,
                  SamePackageConstants.Inner.KEY_11,
                  SamePackageConstants.InnerStatic.KEY_12,
                  KEY_13,
                  KEY_14,
                  SomeEndpoint.Inner.KEY_15,
                  SomeEndpoint.InnerStatic.KEY_16,
                  org.apache.camel.maven.packaging.endpoint.SamePackageConstants.KEY_17,
                  org.apache.camel.maven.packaging.endpoint.other.OtherPackageConstants.KEY_18
          })
public class SomeEndpoint {
    @Deprecated
    @Metadata(description = "key1 desc", label = "my label", displayName = "my display name",
              javaType = "org.apache.camel.maven.packaging.endpoint.SomeEndpoint$MyEnum", required = true,
              defaultValue = "VAL1", deprecationNote = "my deprecated note", secret = true)
    public static final String KEY_1 = "name-1";

    @UriPath(description = "Hostname of the Foo server")
    @Metadata(required = true)
    private String host;

    public final class Inner {
        @Metadata(description = "key13 desc")
        public static final String KEY_13 = "name-13";
        @Metadata(description = "key15 desc")
        public static final String KEY_15 = "name-15";

        private Inner() {
        }
    }

    public static class InnerStatic {
        @Metadata(description = "key14 desc")
        public static final String KEY_14 = "name-14";
        @Metadata(description = "key16 desc")
        public static final String KEY_16 = "name-16";
    }

    public String getHost() {
        return host;
    }

    /**
     * Hostname of the Foo server
     */
    public void setHost(String host) {
        this.host = host;
    }

    public enum MyEnum {
        VAL1,
        VAL2,
        VAL3
    }
}
