package org.apache.camel.spi;

import java.util.Collection;

import org.apache.camel.RoutesBuilder;

/**
 * An extended {@link RoutesBuilderLoader} that is capable of loading from multiple resources in one unit (such as
 * compiling them together).
 */
public interface ExtendedRoutesBuilderLoader extends RoutesBuilderLoader {

    /**
     * Loads {@link RoutesBuilder} from multiple {@link Resource}s.
     *
     * @param  resources the resources to be loaded.
     * @return           a set of loaded {@link RoutesBuilder}s
     */
    Collection<RoutesBuilder> loadRoutesBuilders(Collection<Resource> resources) throws Exception;

}
