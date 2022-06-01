package org.apache.camel.main;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.DefaultResourceLoader;
import org.apache.camel.spi.Resource;
import org.apache.camel.util.StringHelper;

class DependencyDownloaderResourceLoader extends DefaultResourceLoader {

    public DependencyDownloaderResourceLoader(CamelContext camelContext) {
        super(camelContext);
    }

    @Override
    public Resource resolveResource(String uri) {
        String scheme = StringHelper.before(uri, ":");
        if ("github".equals(scheme) || "gist".equals(scheme)) {
            if (!hasResourceResolver(scheme)) {
                // need to download github resolver
                if (!DownloaderHelper.alreadyOnClasspath(
                        getCamelContext(), "org.apache.camel", "camel-resourceresolver-github",
                        getCamelContext().getVersion())) {
                    DownloaderHelper.downloadDependency(getCamelContext(), "org.apache.camel", "camel-resourceresolver-github",
                            getCamelContext().getVersion());
                }
            }
        }
        return super.resolveResource(uri);
    }

}
