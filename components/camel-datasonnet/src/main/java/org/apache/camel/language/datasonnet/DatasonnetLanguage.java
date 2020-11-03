package org.apache.camel.language.datasonnet;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.datasonnet.Mapper;
import com.datasonnet.document.MediaType;
import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.component.PropertyConfigurerSupport;

@Language("datasonnet")
public class DatasonnetLanguage extends LanguageSupport implements PropertyConfigurer {
    // Cache used to stores the Mappers
    // See: {@link GroovyLanguage}
    private final Map<String, Mapper> mapperCache = LRUCacheFactory.newLRUSoftCache(16, 1000, true);

    private MediaType bodyMediaType;
    private MediaType outputMediaType;
    private Class<?> resultType;
    private Collection<String> libraryPaths;

    @Override
    public Predicate createPredicate(String expression) {
        return createPredicate(expression, null);
    }

    @Override
    public Expression createExpression(String expression) {
        return createExpression(expression, null);
    }

    @Override
    public Predicate createPredicate(String expression, Object[] properties) {
        return (Predicate) createExpression(expression, properties);
    }

    @Override
    public Expression createExpression(String expression, Object[] properties) {
        expression = loadResource(expression);

        DatasonnetExpression answer = new DatasonnetExpression(expression);
        answer.setResultType(property(Class.class, properties, 0, resultType));

        String stringBodyMediaType = property(String.class, properties, 1, null);
        answer.setBodyMediaType(stringBodyMediaType != null ? MediaType.valueOf(stringBodyMediaType) : bodyMediaType);
        String stringOutputMediaType = property(String.class, properties, 2, null);
        answer.setOutputMediaType(stringOutputMediaType != null ? MediaType.valueOf(stringOutputMediaType) : outputMediaType);

        return answer;
    }

    Optional<Mapper> lookup(String script) {
        return Optional.ofNullable(mapperCache.get(script));
    }

    Mapper computeIfMiss(String script, Supplier<Mapper> mapperSupplier) {
        return mapperCache.computeIfAbsent(script, (k) -> mapperSupplier.get());
    }

    @Override
    public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
        if (target != this) {
            throw new IllegalStateException("Can only configure our own instance !");
        }

        switch (ignoreCase ? name.toLowerCase() : name) {
            case "bodyMediaType":
            case "bodymediatype":
                setBodyMediaType(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
            case "outputMediaType":
            case "outputmediatype":
                setOutputMediaType(PropertyConfigurerSupport.property(camelContext, String.class, value));
                return true;
            case "resultType":
            case "resulttype":
                setResultType(PropertyConfigurerSupport.property(camelContext, Class.class, value));
                return true;
            default:
                return false;
        }
    }

    // Getter/Setter methods
    // -------------------------------------------------------------------------

    public MediaType getBodyMediaType() {
        return bodyMediaType;
    }

    public void setBodyMediaType(MediaType bodyMediaType) {
        this.bodyMediaType = bodyMediaType;
    }

    public void setBodyMediaType(String bodyMediaType) {
        this.bodyMediaType = MediaType.valueOf(bodyMediaType);
    }

    public MediaType getOutputMediaType() {
        return outputMediaType;
    }

    public void setOutputMediaType(MediaType outputMediaType) {
        this.outputMediaType = outputMediaType;
    }

    public void setOutputMediaType(String outputMediaType) {
        this.outputMediaType = MediaType.valueOf(outputMediaType);
    }

    public Collection<String> getLibraryPaths() {
        return libraryPaths;
    }

    public void setLibraryPaths(Collection<String> libraryPaths) {
        this.libraryPaths = libraryPaths;
    }

    public void setResultType(Class<?> targetType) {
        this.resultType = targetType;
    }
}
