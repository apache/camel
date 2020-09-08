package org.apache.camel.language.datasonnet;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import com.datasonnet.Mapper;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.LanguageSupport;

@Language("datasonnet")
public class DatasonnetLanguage extends LanguageSupport {
    // Cache used to stores the Mappers
    // See: {@link GroovyLanguage}
    private final Map<String, Mapper> mapperCache = LRUCacheFactory.newLRUSoftCache(16, 1000, true);

    public static DatasonnetExpression datasonnet(String expression) {
        return new DatasonnetLanguage().createExpression(expression);
    }

    @Override
    public DatasonnetExpression createPredicate(String expression) {
        return createExpression(expression);
    }

    @Override
    public DatasonnetExpression createExpression(String expression) {
        expression = loadResource(expression);
        return new DatasonnetExpression(expression);
    }

    Optional<Mapper> lookup(String script) {
        return Optional.ofNullable(mapperCache.get(script));
    }

    Mapper cache(String script, Supplier<Mapper> mapperSupplier) {
        return mapperCache.computeIfAbsent(script, (k) -> mapperSupplier.get());
    }
}
