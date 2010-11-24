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
package org.apache.camel.dataformat.bindy;

import java.util.Locale;

import org.apache.camel.dataformat.bindy.kvp.BindyKeyValuePairDataFormat;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public abstract class BindyAbstractDataFormat implements DataFormat {
    private String[] packages;
    private String locale;
    private BindyAbstractFactory modelFactory;

    public BindyAbstractDataFormat() {
    }

    public BindyAbstractDataFormat(String... packages) {
        this.packages = packages;
    }

    public String[] getPackages() {
        return packages;
    }

    public void setPackages(String... packages) {
        this.packages = packages;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }
    
    public BindyAbstractFactory getFactory(PackageScanClassResolver resolver) throws Exception {
        if (modelFactory == null) {
            modelFactory = createModelFactory(resolver);
            modelFactory.setLocale(locale);
        }
        return modelFactory;
    }
    
    public void setModelFactory(BindyAbstractFactory modelFactory) {
        this.modelFactory = modelFactory;
    }
    
    protected abstract BindyAbstractFactory createModelFactory(PackageScanClassResolver resolver) throws Exception;
}
