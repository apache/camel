package org.apache.camel.language.datasonnet;

import org.apache.camel.support.language.LanguageAnnotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Datasonnet
 * TODO: 7/21/20 docs
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER })
@LanguageAnnotation(language = "datasonnet")
public @interface Datasonnet {
    String value();
}
