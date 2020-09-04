package org.apache.camel.language.datasonnet;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import com.datasonnet.Mapper;
import com.datasonnet.MapperBuilder;
import com.datasonnet.document.DefaultDocument;
import com.datasonnet.document.Document;
import com.datasonnet.document.MediaType;
import com.datasonnet.document.MediaTypes;
import com.datasonnet.spi.DataFormatService;
import com.datasonnet.spi.PluginException;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.RuntimeExpressionException;
import org.apache.camel.language.CML;
import org.apache.camel.language.CML$;
import org.apache.camel.spi.GeneratedPropertyConfigurer;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.MessageHelper;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatasonnetExpression extends ExpressionAdapter implements GeneratedPropertyConfigurer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatasonnetExpression.class);
    private static final Map<String, String> CLASSPATH_IMPORTS = new HashMap<>();

    static {
        LOGGER.debug("One time classpath search...");
        try (ScanResult scanResult = new ClassGraph().whitelistPaths("/").scan()) {
            scanResult.getResourcesWithExtension("libsonnet")
                    .forEachByteArray((resource, bytes) -> {
                        LOGGER.debug("Loading DataSonnet library: " + resource.getPath());
                        CLASSPATH_IMPORTS.put(
                                resource.getPath(), new String(bytes, StandardCharsets.UTF_8));
                    });
        }
    }

    private Collection<String> libraryPaths;
    private Expression metaExpression;
    private String expression;
    private MediaType bodyType;
    private MediaType outputType;
    private Class<?> targetType;

    public DatasonnetExpression(String expression) {
        this.expression = expression;
    }

    public DatasonnetExpression(Expression expression) {
        this.metaExpression = expression;
    }

    public DatasonnetExpression usingLibraryPaths(Collection<String> paths) {
        libraryPaths = paths;
        return this;
    }

    @Override
    public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
        if (target != this) {
            throw new IllegalStateException("Can only configure our own instance !");
        }

        switch (ignoreCase ? name.toLowerCase() : name) {
            case "inputMimeType":
            case "inputmimetype":
                setInputMimeType(MediaType.valueOf((String) value));
                return true;
            case "outputMimeType":
            case "outputmimetype":
                setOutputMimeType(MediaType.valueOf((String) value));
                return true;
            case "type":
                try {
                    setType(Class.forName((String) value));
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException("Requested output class type not found", e);
                }
                return true;
        }

        return false;
    }

    @Override
    public boolean matches(Exchange exchange) {
        return evaluate(exchange, Boolean.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        try {
            if (metaExpression != null) {
                expression = metaExpression.evaluate(exchange, String.class);
            }

            Objects.requireNonNull(expression, "String expression property must be set!");

            Map<String, Document<?>> inputs = new HashMap<>();

            Document<?> headersDocument = mapToDocument(exchange.getMessage().getHeaders());
            inputs.put("headers", headersDocument);
            inputs.put("header", headersDocument);

            Document<?> propertiesDocument = mapToDocument(exchange.getProperties());
            inputs.put("exchangeProperty", propertiesDocument);

            Document<?> body;

            if (bodyType == null) {
                //Try to auto-detect input mime type if it was not explicitly set
                String typeHeader = exchange.getProperty("inputMimeType",
                        exchange.getIn().getHeader(Exchange.CONTENT_TYPE,
                                "UNKNOWN_MIME_TYPE"),
                        String.class);
                if (!"UNKNOWN_MIME_TYPE".equalsIgnoreCase(typeHeader) && typeHeader != null) {
                    bodyType = MediaType.valueOf(typeHeader);
                }
            }

            if (exchange.getMessage().getBody() instanceof Document) {
                body = (Document<?>) exchange.getMessage().getBody();
            } else if (MediaTypes.APPLICATION_JAVA.equalsTypeAndSubtype(bodyType)) {
                body = new DefaultDocument<>(exchange.getMessage().getBody());
            } else {
                body = new DefaultDocument<>(MessageHelper.extractBodyAsString(exchange.getMessage()), bodyType);
            }

            inputs.put("body", body);

            DatasonnetLanguage language = (DatasonnetLanguage) exchange.getContext().resolveLanguage("datasonnet");
            Mapper mapper = language.getMapperFromCache(expression).orElseGet(() -> {
                Mapper answer = new MapperBuilder(expression)
                        .withInputNames(inputs.keySet())
                        .withImports(resolveImports())
                        .addLibrary(CML$.MODULE$)
                        .build();
                language.addScriptToCache(expression, answer);
                return answer;
            });

            // set exchange and variable resolver as thread locals for concurrency
            CML.exchange().set(exchange);

            if (outputType == null) {
                //Try to auto-detect output mime type if it was not explicitly set
                String typeHeader = exchange.getProperty("outputMimeType",
                        exchange.getIn().getHeader("outputMimeType",
                                "UNKNOWN_MIME_TYPE"),
                        String.class);
                if (!"UNKNOWN_MIME_TYPE".equalsIgnoreCase(typeHeader) && typeHeader != null) {
                    outputType = MediaType.parseMediaType(typeHeader);
                }
            }

            if (Document.class.equals(type)) {
                return (T) mapper.transform(body, inputs, outputType, Object.class);
            } else if (!type.equals(Object.class)) {
                return mapper.transform(body, inputs, outputType, type).getContent();
            } else if (targetType != null) {
                // only if type _is_ Object.class and targetType exists use targetType
                return (T) mapper.transform(body, inputs, outputType, targetType).getContent();
            } else {
                // else use type
                return mapper.transform(body, inputs, outputType, type).getContent();
            }

        } catch (Exception e) {
            throw new RuntimeExpressionException("Unable to evaluate DataSonnet expression : " + expression, e);
        } finally {
            CML.exchange().remove();
        }
    }

    private Map<String, String> resolveImports() {
        if (libraryPaths == null) {
            return CLASSPATH_IMPORTS;
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

    private Document<Map<String, Object>> mapToDocument(Map<String, Object> map) throws PluginException {
        Iterator<Map.Entry<String, Object>> entryIterator = map.entrySet().iterator();
        Map<String, Object> propsMap = new HashMap<>();

        while (entryIterator.hasNext()) {
            Map.Entry<String, Object> entry = entryIterator.next();
            String key = entry.getKey();
            Object value = entry.getValue();

            if (!(value instanceof Document)) {
                propsMap.put(key, value);
            } else {
                if (MediaTypes.APPLICATION_JAVA.equalsTypeAndSubtype(((Document<?>) value).getMediaType())) {
                    propsMap.put(key, ((Document<?>) value).getContent());
                } else {
                    // TODO: 9/2/20 where to get this from
                    // TODO: 9/2/20 figure out how to avoid the conversion round trip
                    DataFormatService service = null;
                    ujson.Value read = service.thatAccepts((Document<?>) value)
                            .orElseThrow(() -> new IllegalArgumentException("todo"))
                            .read((Document<?>) value);
                    Document<?> write = service.thatProduces(MediaTypes.APPLICATION_JAVA, Object.class)
                            .orElseThrow(() -> new IllegalArgumentException("todo"))
                            .write(read, MediaTypes.APPLICATION_JAVA, Object.class);
                    propsMap.put(key, write);
                }
            }
        }

        return new DefaultDocument<>(propsMap);
    }

    public MediaType getInputMimeType() {
        return bodyType;
    }

    /**
     * TODO: 7/21/20 docs
     *
     * @param inputMimeType docs
     */
    public void setInputMimeType(MediaType inputMimeType) {
        this.bodyType = inputMimeType;
    }

    public MediaType getOutputMimeType() {
        return outputType;
    }

    /**
     * TODO: 7/21/20 docs
     *
     * @param outputMimeType docs
     */
    public void setOutputMimeType(MediaType outputMimeType) {
        this.outputType = outputMimeType;
    }

    public Expression getMetaExpression() {
        return metaExpression;
    }

    /**
     * TODO: 7/21/20 docs
     *
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
     *
     * @param libraryPaths docs
     */
    public void setLibraryPaths(Collection<String> libraryPaths) {
        this.libraryPaths = libraryPaths;
    }

    /**
     * // TODO: 9/3/20 docs
     *
     * @return
     */
    public Class<?> getType() {
        return this.targetType;
    }

    public void setType(Class<?> targetType) {
        this.targetType = targetType;
    }

    @Override
    public String toString() {
        return "datasonnet: " + expression;
    }

}
