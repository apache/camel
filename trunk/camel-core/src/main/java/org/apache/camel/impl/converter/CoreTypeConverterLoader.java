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
package org.apache.camel.impl.converter;

import java.io.IOException;

/**
 * Will load all type converters from camel-core without classpath scanning, which makes
 * it much faster.
 * <p/>
 * The {@link CorePackageScanClassResolver} contains a hardcoded list of the type converter classes to load.
 */
public class CoreTypeConverterLoader extends AnnotationTypeConverterLoader {

    public CoreTypeConverterLoader() {
        super(new CorePackageScanClassResolver());
    }

    @Override
    protected String[] findPackageNames() throws IOException {
        // this method doesn't change the behavior of the CorePackageScanClassResolver
        return new String[]{"org.apache.camel.converter", "org.apache.camel.component.bean", "org.apache.camel.component.file"};
    }

}
