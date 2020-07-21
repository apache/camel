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
package org.apache.camel.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;

import org.apache.camel.CamelContext;

public class CamelObjectInputStream extends ObjectInputStream {
    private final ClassLoader classLoader;
    
    public CamelObjectInputStream(InputStream in, CamelContext context) throws IOException {
        super(in);
        if (context != null) {
            this.classLoader = context.getApplicationContextClassLoader();
        } else {
            this.classLoader = null;
        }
    }
    
    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws ClassNotFoundException, IOException {
        if (classLoader != null) {
            return Class.forName(desc.getName(), false, classLoader);
        } else {
            // If the application classloader is not set we just fallback to use old behaivor
            return super.resolveClass(desc);
        }
    }
}
