/* Generated by camel build tools - do NOT edit this file! */
package org.apache.camel.component.langchain.embeddings;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.ExtendedPropertyConfigurerGetter;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.apache.camel.spi.ConfigurerStrategy;
import org.apache.camel.spi.GeneratedPropertyConfigurer;
import org.apache.camel.util.CaseInsensitiveMap;
import org.apache.camel.component.langchain.embeddings.LangChainEmbeddingsConfiguration;

/**
 * Generated by camel build tools - do NOT edit this file!
 */
@SuppressWarnings("unchecked")
public class LangChainEmbeddingsConfigurationConfigurer extends org.apache.camel.support.component.PropertyConfigurerSupport implements GeneratedPropertyConfigurer, PropertyConfigurerGetter {

    @Override
    public boolean configure(CamelContext camelContext, Object obj, String name, Object value, boolean ignoreCase) {
        org.apache.camel.component.langchain.embeddings.LangChainEmbeddingsConfiguration target = (org.apache.camel.component.langchain.embeddings.LangChainEmbeddingsConfiguration) obj;
        switch (ignoreCase ? name.toLowerCase() : name) {
        case "embeddingmodel":
        case "embeddingModel": target.setEmbeddingModel(property(camelContext, dev.langchain4j.model.embedding.EmbeddingModel.class, value)); return true;
        default: return false;
        }
    }

    @Override
    public Class<?> getOptionType(String name, boolean ignoreCase) {
        switch (ignoreCase ? name.toLowerCase() : name) {
        case "embeddingmodel":
        case "embeddingModel": return dev.langchain4j.model.embedding.EmbeddingModel.class;
        default: return null;
        }
    }

    @Override
    public Object getOptionValue(Object obj, String name, boolean ignoreCase) {
        org.apache.camel.component.langchain.embeddings.LangChainEmbeddingsConfiguration target = (org.apache.camel.component.langchain.embeddings.LangChainEmbeddingsConfiguration) obj;
        switch (ignoreCase ? name.toLowerCase() : name) {
        case "embeddingmodel":
        case "embeddingModel": return target.getEmbeddingModel();
        default: return null;
        }
    }
}
