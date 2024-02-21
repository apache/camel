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
package org.apache.camel.main.download;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.dsl.support.DefaultSourceLoader;
import org.apache.camel.spi.Resource;

/**
 * {@link org.apache.camel.dsl.support.SourceLoader} that can enrich the source with a package name if the code does not
 * have anyone.
 */
public class PackageNameSourceLoader extends DefaultSourceLoader {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "^\\s*package\\s+([a-zA-Z][.\\w]*)\\s*;.*$", Pattern.MULTILINE);

    private final String packageName;

    public PackageNameSourceLoader(String packageName) {
        this.packageName = packageName;
    }

    @Override
    public String loadResource(Resource resource) throws IOException {
        String code = super.loadResource(resource);

        // for java source then insert package name in top of file if none exists
        String loc = resource.getLocation();
        if (loc != null && loc.endsWith(".java")) {
            String pn = determineClassName(code);
            if (pn == null) {
                // insert default package name in top
                // (avoid new-lines so source code lines does not get changed)
                code = "package " + packageName + "; " + code;
            }
        }
        return code;
    }

    private static String determineClassName(String content) {
        Matcher matcher = PACKAGE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

}
