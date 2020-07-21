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
package org.apache.camel.component.weka;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;

import io.nessus.weka.Dataset;
import org.apache.camel.Converter;
import weka.core.Instances;

@Converter(generateLoader = true)
public final class WekaTypeConverters {

    private WekaTypeConverters() {
    }
    
    @Converter
    public static InputStream toInputStream(Dataset dataset) {
        Instances instances = dataset.getInstances();
        return toInputStream(instances);
    }

    @Converter
    public static InputStream toInputStream(Instances instances) {
        byte[] bytes = instances.toString().getBytes();
        return new ByteArrayInputStream(bytes);
    }

    @Converter
    public static Dataset toDataset(Instances instances) {
        return Dataset.create(instances);
    }

    @Converter
    public static Dataset toDataset(InputStream input) {
        return Dataset.create(input);
    }

    @Converter
    public static Dataset toDataset(File infile) {
        return Dataset.create(infile.toPath());
    }

    @Converter
    public static Dataset toDataset(Path inpath) {
        return Dataset.create(inpath);
    }

    @Converter
    public static Dataset toDataset(URL url) {
        return Dataset.create(url);
    }
}
