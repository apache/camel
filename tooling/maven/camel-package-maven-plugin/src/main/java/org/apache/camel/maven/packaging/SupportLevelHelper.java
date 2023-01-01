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
package org.apache.camel.maven.packaging;

import org.apache.camel.tooling.model.SupportLevel;
import org.apache.camel.tooling.util.CamelVersionHelper;
import org.apache.camel.tooling.util.Version;

public final class SupportLevelHelper {

    private SupportLevelHelper() {
    }

    public static SupportLevel defaultSupportLevel(String firstVersion, String currentVersion) {
        if (firstVersion == null || firstVersion.isEmpty()) {
            throw new IllegalArgumentException(
                    "FirstVersion is not specified. This can be done in @UriEndpoint or in pom.xml file.");
        }

        // we only want major/minor (strip patch)
        Version v1 = new Version(firstVersion);
        v1 = new Version(v1.getMajor() + "." + v1.getMinor());
        Version v2 = new Version(currentVersion);
        v2 = new Version(v2.getMajor() + "." + v2.getMinor());

        boolean justNew = CamelVersionHelper.isGE(v2.toString(), v1.toString());
        boolean prevNew = CamelVersionHelper.isGE(CamelVersionHelper.prevMinor(v2.toString()), v1.toString());
        if (justNew || prevNew) {
            // its a new component (2 releases back) that is added to this version so lets mark it as preview by default
            return SupportLevel.Preview;
        } else {
            return SupportLevel.Stable;
        }
    }
}
