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
package org.apache.camel.component.file;

import java.io.File;
import java.io.Serializable;

/**
 * File binding with the {@link java.io.File} type.
 */
public class NewFileBinding implements GenericFileBinding<File>, Serializable {

    private File body;

    public Object getBody(GenericFile<File> file) {
        // as we use java.io.File itself as the body (not loading its content into a OutputStream etc.)
        // we just store a java.io.File handle to the actual file denoted by the
        // file.getAbsoluteFileName. We must do this as the original file consumed can be renamed before
        // being processed (preMove) and thus it points to an invalid file location.
        // GenericFile#getAbsoluteFileName() is always up-to-date and thus we use it to create a file
        // handle that is correct
        if (body == null || !file.getAbsoluteFileName().equals(body.getAbsolutePath())) {
            body = new File(file.getAbsoluteFileName());
        }
        return body;
    }

    public void setBody(GenericFile<File> file, Object body) {
        // noop
    }

}
