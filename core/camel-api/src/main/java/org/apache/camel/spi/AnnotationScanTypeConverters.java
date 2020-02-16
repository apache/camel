package org.apache.camel.spi;

/**
 * A {@link org.apache.camel.TypeConverter} which is capable of annotation scanning for {@link org.apache.camel.Converter}
 * classes and add these as type converters.
 * <p/>
 * This is using Camel 2.x style and its recommended to migrate to @Converter(loader = true) for fast type converter mode.
 */
public interface AnnotationScanTypeConverters {

    /**
     * Scan for {@link org.apache.camel.Converter} classes and add those as type converters.
     *
     * @throws Exception is thrown if error happened
     */
    void scanTypeConverters() throws Exception;
}
