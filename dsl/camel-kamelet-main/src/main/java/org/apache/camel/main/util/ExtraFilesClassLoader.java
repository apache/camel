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
package org.apache.camel.main.util;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Classloader used to load the extra files that were present in the CLI arguments
 */
public final class ExtraFilesClassLoader extends ClassLoader {

    final String[] files;

    public ExtraFilesClassLoader(ClassLoader parent, String[] files) {
        super(parent);
        this.files = files;
    }

    @Override
    protected URL findResource(String name) {
        return getResource(name);
    }

    @Override
    public URL getResource(String name) {
        for (String n : files) {
            if (name.equals(n)) {
                try {
                    return new File(name).toURI().toURL();
                } catch (MalformedURLException e) {
                    // ignore
                }
            }
        }
        return null;
    }

}
