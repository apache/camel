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
package org.apache.camel.dsl.java.joor;

import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dsl.support.RouteBuilderLoaderSupport;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.joor.Reflect;

@ManagedResource(description = "Managed JavaRoutesBuilderLoader")
@RoutesLoader(JavaRoutesBuilderLoader.EXTENSION)
public class JavaRoutesBuilderLoader extends RouteBuilderLoaderSupport {
    public static final String EXTENSION = "java";
    public static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "^\\s*package\\s+([a-zA-Z][\\.\\w]*)\\s*;.*$", Pattern.MULTILINE);

    public JavaRoutesBuilderLoader() {
        super(EXTENSION);
    }

    @Override
    public RouteBuilder doLoadRouteBuilder(Resource resource) throws Exception {
        try (InputStream is = resource.getInputStream()) {
            final String content = IOHelper.loadText(is);
            final String name = determineName(resource, content);

            return Reflect.compile(name, content).create().get();
        }
    }

    private static String determineName(Resource resource, String content) {
        String loc = resource.getLocation();
        // strip scheme to compute the name
        String scheme = ResourceHelper.getScheme(loc);
        if (scheme != null) {
            loc = loc.substring(scheme.length());
        }
        final String name = FileUtil.onlyName(loc, true);
        final Matcher matcher = PACKAGE_PATTERN.matcher(content);

        return matcher.find()
                ? matcher.group(1) + "." + name
                : name;
    }
}
