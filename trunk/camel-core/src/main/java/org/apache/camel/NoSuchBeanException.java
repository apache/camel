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
package org.apache.camel;

/**
 * A runtime exception if a given bean could not be found in the {@link org.apache.camel.spi.Registry}
 *
 * @version 
 */
public class NoSuchBeanException extends RuntimeCamelException {
    private static final long serialVersionUID = -8721487431101572630L;
    private final String name;

    public NoSuchBeanException(String name) {
        super("No bean could be found in the registry for: " + name);
        this.name = name;
    }

    public NoSuchBeanException(String name, String type) {
        super("No bean could be found in the registry" + (name != null ? " for: " + name : "") + " of type: " + type);
        this.name = name;
    }

    public NoSuchBeanException(String name, Throwable cause) {
        super("No bean could be found in the registry for: " + name + ". Cause: " + cause.getMessage(), cause);
        this.name = name;
    }

    public NoSuchBeanException(String name, String message, Throwable cause) {
        super(message, cause);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}