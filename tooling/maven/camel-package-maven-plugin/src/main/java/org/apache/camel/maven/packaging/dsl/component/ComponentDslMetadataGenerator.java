package org.apache.camel.maven.packaging.dsl.component;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.camel.maven.packaging.dsl.DslHelper;
import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.tooling.util.JSonSchemaHelper;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.tooling.util.PackageHelper.loadText;

public class ComponentDslMetadataGenerator {

    private static Map<String, Object> componentsList;
    private final File componentDir;
    private final String componentDslJavaPackage;

    private final Gson gson = new Gson();

    public ComponentDslMetadataGenerator(final File componentDir, final File metadataFile, final String componentDslJavaPackage) {
        this.componentDir = componentDir;
        this.componentDslJavaPackage = componentDslJavaPackage;
        // First: Load the content of the metadata file into memory
        componentsList = loadMetadataFileIntoMap(metadataFile);
        // Second: Read only the file names of the generated DSL classes in order to sync the metadata file
        // If there is a component in the memory but not in the dir, then we shall delete it from the memory
    }

    private Map<String, Object> loadMetadataFileIntoMap(final File metadataFile) {
        return gson.fromJson(loadJson(metadataFile), new TypeToken<Map<String, Object>>() {}.getType());
    }

    private void syncMetadataFileWithGeneratedDslComponents() {
        final List<File> generatedComponents = DslHelper.loadAllJavaFiles(componentDir, componentDslJavaPackage);
    }

    public void addComponentToMetadataAndUpdateMetadataFile(final ComponentModel componentModel, final String key) {
        // Third: Update component model into the hashmap memory
        componentsList.put(key, convertComponentModelToMap(componentModel));
        // Fourth: Write back into the hashmap memory
        // Fifth: Return back the hashmap memory for the user to be used
    }

    public void addComponentToMetadata(final ComponentModel componentModel, final String key) {
        componentsList.put(key, convertComponentModelToMap(componentModel));
    }

    private Map<String, Object> convertComponentModelToMap(final ComponentModel componentModel) {
        final Map<java.lang.String, java.lang.Object> componentMap = new HashMap<>();

        componentMap.put("schema", componentModel.getScheme());
        componentMap.put("title", componentModel.getTitle());
        componentMap.put("description", componentModel.getDescription());
        componentMap.put("label", componentModel.getLabel());
        componentMap.put("deprecated", componentModel.getDeprecated());
        componentMap.put("deprecationNote", componentModel.getDeprecationNote());
        componentMap.put("consumerOnly", componentModel.getConsumerOnly());
        componentMap.put("producerOnly", componentModel.getConsumerOnly());
        componentMap.put("javaType", componentModel.getJavaType());
        componentMap.put("firstVersion", componentModel.getFirstVersion());
        componentMap.put("groupId", componentModel.getGroupId());
        componentMap.put("artifactId", componentModel.getArtifactId());
        componentMap.put("version", componentModel.getVersion());

        return componentMap;
    }

    public Map<String, Object> getComponentListFromMemory() {
        return componentsList;
    }

    public String toJson() {
        return gson.toJson(componentsList);
    }

    private static String loadJson(File file) {
        try {
            return loadText(file);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

}
