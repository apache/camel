package org.apache.camel.yaml.io;

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.catalog.JSonSchemaResolver;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

public class ModelJSonSchemaResolver implements JSonSchemaResolver {

    @Override
    public void setClassLoader(ClassLoader classLoader) {

    }

    @Override
    public String getComponentJSonSchema(String name) {
        return null;
    }

    @Override
    public String getDataFormatJSonSchema(String name) {
        return null;
    }

    @Override
    public String getLanguageJSonSchema(String name) {
        return null;
    }

    @Override
    public String getOtherJSonSchema(String name) {
        return null;
    }

    @Override
    public String getModelJSonSchema(String name) {
        try {
            String[] subPackages = new String[] {
                    "", "cloud/", "config/", "dataformat/", "errorhandler/", "language/", "loadbalancer/", "rest/",
                    "transformer/", "validator/" };
            for (String sub : subPackages) {
                String path = CamelContextHelper.MODEL_DOCUMENTATION_PREFIX + sub + name + ".json";
                String inputStream = doLoadResource(name, path, "eip");
                if (inputStream != null) {
                    return inputStream;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
            // ignore
        }
        return null;
    }

    @Override
    public String getMainJsonSchema() {
        return null;
    }

    private String doLoadResource(String resourceName, String path, String resourceType) throws IOException {
        InputStream inputStream = ObjectHelper.loadResourceAsStream(path, Thread.currentThread().getContextClassLoader());
        if (inputStream != null) {
            try {
                return IOHelper.loadText(inputStream);
            } finally {
                IOHelper.close(inputStream);
            }
        }
        return null;
    }

}
