package org.apache.camel.language.datasonnet;

import com.datasonnet.Mapper;
import com.datasonnet.document.Document;
import com.datasonnet.document.JavaObjectDocument;
import com.datasonnet.document.StringDocument;
import com.datasonnet.spi.DataFormatPlugin;
import com.datasonnet.spi.DataFormatService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.Resource;
import io.github.classgraph.ResourceList;
import io.github.classgraph.ScanResult;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.RuntimeExpressionException;
import org.apache.camel.spi.GeneratedPropertyConfigurer;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.component.PropertyConfigurerSupport;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DatasonnetExpression extends ExpressionAdapter implements GeneratedPropertyConfigurer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatasonnetExpression.class);
    private static final Map<String, String> classpathImports = new HashMap<>();
    private static final ObjectMapper jacksonMapper = new ObjectMapper();

    static {
        LOGGER.debug("One time classpath search...");
        try (ScanResult scanResult = new ClassGraph().whitelistPaths("/").scan()) {
            scanResult.getResourcesWithExtension("libsonnet")
                    .forEachByteArray(new ResourceList.ByteArrayConsumer() {
                        @Override
                        public void accept(Resource resource, byte[] bytes) {
                            LOGGER.debug("Loading DataSonnet library: " + resource.getPath());
                            classpathImports.put(
                                    resource.getPath(), new String(bytes, StandardCharsets.UTF_8));
                        }
                    });
        }

        jacksonMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        jacksonMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    private final List<String> supportedMimeTypes = new ArrayList<>(Arrays.asList("application/json"));
    private Collection<String> libraryPaths;
    private Expression metaExpression;
    private String expression;
    private String inputMimeType;
    private String outputMimeType;

    public DatasonnetExpression(String expression) {
        this.expression = expression;
        findSupportedMimeTypes();
    }

    public DatasonnetExpression(Expression expression) {
        this.metaExpression = expression;
        findSupportedMimeTypes();
    }

    public DatasonnetExpression usingLibraryPaths(Collection<String> paths) {
        libraryPaths = paths;
        return this;
    }

    // TODO: 7/21/20 maybe we can do this statically?
    private void findSupportedMimeTypes() {
        List<DataFormatPlugin> pluginsList = new DataFormatService().findPlugins();
        for (DataFormatPlugin plugin : pluginsList) {
            supportedMimeTypes.addAll(Arrays.asList(plugin.getSupportedIdentifiers()));
        }
    }

    @Override
    public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
        if (target != this) {
            throw new IllegalStateException("Can only configure our own instance !");
        }

        switch (ignoreCase ? name.toLowerCase() : name) {
            case "inputMimeType":
            case "inputmimetype":
                setInputMimeType(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
            case "outputMimeType":
            case "outputmimetype":
                setOutputMimeType(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
        }

        return false;
    }

    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        try {
            if (metaExpression != null) {
                expression = metaExpression.evaluate(exchange, String.class);
            }

            Object value = processMapping(exchange);
            return exchange.getContext().getTypeConverter().convertTo(type, value);
        } catch (Exception e) {
            throw new RuntimeExpressionException("Unable to evaluate DataSonnet expression : " + expression, e);
        }
    }

    public Object processMapping(Exchange exchange) throws Exception {
        if (inputMimeType == null || "".equalsIgnoreCase(inputMimeType.trim())) {
            //Try to auto-detect input mime type if it was not explicitly set
            String overriddenInputMimeType = (String) exchange.getProperty("inputMimeType",
                    exchange.getIn().getHeader(Exchange.CONTENT_TYPE,
                            "UNKNOWN_MIME_TYPE"));
            if (!"UNKNOWN_MIME_TYPE".equalsIgnoreCase(overriddenInputMimeType) && overriddenInputMimeType != null) {
                inputMimeType = overriddenInputMimeType;
            }
        }

        if (!supportedMimeTypes.contains(inputMimeType)) {
            LOGGER.warn("Input Mime Type " + inputMimeType + " is not supported or suitable plugin not found, using application/json");
            inputMimeType = "application/json";
        }

        if (outputMimeType == null || "".equalsIgnoreCase(outputMimeType.trim())) {
            //Try to auto-detect output mime type if it was not explicitly set
            String overriddenOutputMimeType = (String) exchange.getProperty("outputMimeType",
                    exchange.getIn().getHeader("outputMimeType",
                            "UNKNOWN_MIME_TYPE"));
            if (!"UNKNOWN_MIME_TYPE".equalsIgnoreCase(overriddenOutputMimeType) && overriddenOutputMimeType != null) {
                outputMimeType = overriddenOutputMimeType;
            }
        }

        if (!supportedMimeTypes.contains(outputMimeType)) {
            LOGGER.warn("Output Mime Type " + outputMimeType + " is not supported or suitable plugin not found, using application/json");
            outputMimeType = "application/json";
        }

        if (expression == null) {
            throw new IllegalArgumentException("String expression property must be set!");
        }

        Map<String, Document> jsonnetVars = new HashMap<>();

        Document headersDocument = mapToDocument(exchange.getMessage().getHeaders());
        jsonnetVars.put("headers", headersDocument);
        jsonnetVars.put("header", headersDocument);

        Document propertiesDocument = mapToDocument(exchange.getProperties());
        jsonnetVars.put("exchangeProperty", propertiesDocument);

        Object body = (inputMimeType.contains("java") ? exchange.getMessage().getBody() : MessageHelper.extractBodyAsString(exchange.getMessage()));

        LOGGER.debug("Input MIME type is " + inputMimeType);
        LOGGER.debug("Output MIME type is: " + outputMimeType);
        LOGGER.debug("Message Body is " + body);
        LOGGER.debug("Variables are: " + jsonnetVars);

        //TODO we need a better solution going forward but for now we just differentiate between Java and text-based formats
        Document payload = createDocument(body, inputMimeType);
        jsonnetVars.put("body", payload);

        LOGGER.debug("Document is: " + (payload.canGetContentsAs(String.class) ? payload.getContentsAsString() : payload.getContentsAsObject()));

        DatasonnetLanguage language = (DatasonnetLanguage) exchange.getContext().resolveLanguage("datasonnet");
        Mapper mapper = language.getMapperFromCache(expression).orElseGet(() -> {
            Mapper answer = new Mapper(expression, jsonnetVars.keySet(), resolveImports(), true, true);
            language.addScriptToCache(expression, answer);
            return answer;
        });

        Document mappedDoc = mapper.transform(payload, jsonnetVars, getOutputMimeType());
        Object mappedBody = mappedDoc.canGetContentsAs(String.class) ? mappedDoc.getContentsAsString() : mappedDoc.getContentsAsObject();

        return mappedBody;
    }

    private Map<String, String> resolveImports() {
        if (libraryPaths == null) {
            return classpathImports;
        }

        Map<String, String> answer = new HashMap<>();
        LOGGER.debug("Explicit library path is " + libraryPaths);
        for (String nextPath : libraryPaths) {
            final File nextLibDir = new File(nextPath);
            if (nextLibDir.isDirectory()) {
                try {
                    Files.walkFileTree(nextLibDir.toPath(), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            File f = file.toFile();
                            if (!f.isDirectory() && f.getName().toLowerCase().endsWith(".libsonnet")) {
                                String content = IOUtils.toString(file.toUri());
                                Path relative = nextLibDir.toPath().relativize(file);
                                LOGGER.debug("Loading DataSonnet library: " + relative);
                                answer.put(relative.toString(), content);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException e) {
                    LOGGER.error("Unable to load libraries from " + nextPath, e);
                }
            }
        }

        return answer;
    }

    private Document createDocument(Object content, String type) throws JsonProcessingException {
        Document document = null;
        boolean isObject = false;
        String mimeType = type;
        String documentContent = (content == null ? "" : content.toString());

        LOGGER.debug("Before create Document Content is: " + documentContent);
        LOGGER.debug("Before create mimeType is: " + mimeType);

        if (mimeType.contains("/xml")) {
            mimeType = "application/xml";
        } else if (mimeType.contains("/csv")) {
            mimeType = "application/csv";
        } else if (mimeType.contains("/java")) {
            mimeType = "application/java";
            isObject = true;
        } else {
            mimeType = "application/json";
            try {
                if (documentContent == null || "".equalsIgnoreCase(documentContent)) {
                    documentContent = "{}";
                }
                jacksonMapper.readTree(documentContent);
                LOGGER.debug("Content is valid JSON");
                //This is valid JSON
            } catch (Exception e) {
                //Not a valid JSON, convert
                LOGGER.debug("Content is not valid JSON, converting to JSON string");
                documentContent = jacksonMapper.writeValueAsString(content);
            }
        }

        LOGGER.debug("Document Content is: " + documentContent);

        document = isObject ? new JavaObjectDocument(content) : new StringDocument(documentContent, mimeType);

        return document;
    }

    private Document mapToDocument(Map<String, Object> map) throws Exception {
        Iterator<Map.Entry<String, Object>> entryIterator = map.entrySet().iterator();
        Map<String, Object> propsMap = new HashMap<>();

        while (entryIterator.hasNext()) {
            Map.Entry<String, Object> entry = entryIterator.next();

            Object entryValue = entry.getValue();
            String entryClassName = (entryValue != null ? entryValue.getClass().getName() : " NULL ");

            if (entryValue != null && entryValue instanceof Serializable) {
                try {
                    jacksonMapper.writeValueAsString(entryValue);
                    propsMap.put(entry.getKey(), entryValue);
                } catch (Exception e) {
                    LOGGER.debug("Header or property " + entry.getKey() + " cannot be serialized as JSON; removing : " + e.getMessage());
                }
            } else {
                LOGGER.debug("Header or property " + entry.getKey() + " is null or not Serializable : " + entryClassName);
            }
        }

        Document document = new JavaObjectDocument(propsMap);
        return document;
    }

    public String getInputMimeType() {
        return inputMimeType;
    }

    /**
     * TODO: 7/21/20 docs
     * @param inputMimeType docs
     */
    public void setInputMimeType(String inputMimeType) {
        this.inputMimeType = inputMimeType;
    }

    public String getOutputMimeType() {
        return outputMimeType;
    }

    /**
     * TODO: 7/21/20 docs
     * @param outputMimeType docs
     */
    public void setOutputMimeType(String outputMimeType) {
        this.outputMimeType = outputMimeType;
    }

    public Expression getMetaExpression() {
        return metaExpression;
    }

    /**
     * TODO: 7/21/20 docs
     * @param metaExpression docs
     */
    public void setMetaExpression(Expression metaExpression) {
        this.metaExpression = metaExpression;
    }

    public Collection<String> getLibraryPaths() {
        return libraryPaths;
    }

    /**
     * TODO: 7/21/20 docs
     * @param libraryPaths docs
     */
    public void setLibraryPaths(Collection<String> libraryPaths) {
        this.libraryPaths = libraryPaths;
    }

    @Override
    public String toString() {
        return "datasonnet: " + expression;
    }

}