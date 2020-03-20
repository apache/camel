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
package org.apache.camel.maven.packaging.dsl.component;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.camel.maven.packaging.dsl.DslHelper;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.FileUtil;
import org.apache.camel.tooling.util.Strings;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.tooling.util.PackageHelper.loadText;

/**
 * Metadata components registry, used to keep track of the components generated DSLs in order to sync the pom file and relevant main builder factory file
 */
public class ComponentsDslMetadataRegistry {

    private Map<String, ComponentModel> componentsCache;
    private Set<String> componentsDslFactories;
    private File metadataFile;

    public ComponentsDslMetadataRegistry(final File componentDslDir, final File metadataFile) {
        // First: Load the content of the metadata file into memory
        componentsCache = loadMetadataFileIntoMap(metadataFile);
        componentsDslFactories = loadComponentsFactoriesFromDir(componentDslDir);
        this.metadataFile = metadataFile;
    }

    private Map<String, ComponentModel> loadMetadataFileIntoMap(final File metadataFile) {
        String json;
        if (metadataFile.isFile()) {
            json = loadJson(metadataFile);
        } else {
            json = "{ }";
        }
        JsonObject jsonObject = JsonMapper.deserialize(json);
        Map<String, ComponentModel> models = new TreeMap<>();
        jsonObject.forEach((jsonKey, jsonValue) -> models.put(jsonKey, loadModel((JsonObject) jsonValue)));
        return models;
    }

    private ComponentModel loadModel(JsonObject json) {
        final ComponentModel model = new ComponentModel();
        JsonMapper.parseComponentModel(json, model);
        return model;
    }

    private Set<String> loadComponentsFactoriesFromDir(final File componentDir) {
        return DslHelper.loadAllJavaFiles(componentDir).stream()
                .map(file -> Strings.before(file.getName(), "."))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public boolean addComponentToMetadataAndSyncMetadataFile(final ComponentModel componentModel, final String key) {
        // put the component into the cache
        componentsCache.put(key, new ModifiedComponentModel(componentModel));

        return syncMetadataFile();
    }

    private boolean syncMetadataFile() {
        syncMetadataFileWithGeneratedDslComponents();
        return writeCacheIntoMetadataFile();
    }

    private void syncMetadataFileWithGeneratedDslComponents() {
        // First: We check if there is a component in the memory but not in the dir, then we shall delete it from the memory
        final Set<String> componentsNamesToRemoveFromCache = new HashSet<>();
        componentsCache.forEach((componentFactoryName, value) -> {
            if (!componentsDslFactories.contains(componentFactoryName)) {
                // remove the component from the metadata
                componentsNamesToRemoveFromCache.add(componentFactoryName);
            }
        });

        componentsNamesToRemoveFromCache.forEach(componentFactoryName -> componentsCache.remove(componentFactoryName));
    }

    private boolean writeCacheIntoMetadataFile() {
        JsonObject json = new JsonObject();
        componentsCache.forEach((componentKey, componentModel) -> json.put(componentKey, JsonMapper.asJsonObject(componentModel).get("component")));
        final String jsonText = JsonMapper.serialize(json);
        try {
            return FileUtil.updateFile(metadataFile.toPath(), jsonText);
        } catch (IOException ex) {
            throw new IOError(ex);
        }
    }

    public Map<String, ComponentModel> getComponentCacheFromMemory() {
        return componentsCache;
    }

    private static String loadJson(File file) {
        try {
            return loadText(file);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    private static class ModifiedComponentModel extends ComponentModel {
        public ModifiedComponentModel(final ComponentModel componentModel) {
            name = componentModel.getName();
            title = componentModel.getTitle();
            description = componentModel.getDescription();
            firstVersion = componentModel.getFirstVersion();
            javaType = componentModel.getJavaType();
            label = componentModel.getLabel();
            deprecated = componentModel.isDeprecated();
            deprecationNote = componentModel.getDeprecationNote();
            scheme = componentModel.getScheme();
            extendsScheme = componentModel.getExtendsScheme();
            alternativeSchemes = componentModel.getAlternativeSchemes();
            syntax = componentModel.getSyntax();
            alternativeSyntax = componentModel.getAlternativeSyntax();
            async = componentModel.isAsync();
            consumerOnly = componentModel.isConsumerOnly();
            producerOnly = componentModel.isProducerOnly();
            lenientProperties = componentModel.isLenientProperties();
            verifiers = componentModel.getVerifiers();
            groupId = componentModel.getGroupId();
            artifactId = componentModel.getArtifactId();
            version = componentModel.getVersion();
        }
    }

}
