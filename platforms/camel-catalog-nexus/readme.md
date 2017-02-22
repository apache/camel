## catalog-nexus

The catalog-nexus is an extension to the Camel Catalog which can be used 
to index a Nexus Maven repository and discover new Camel Components and
add them to the CamelCatalog.

For example to index an in-house nexus repository and allow developers to
discover what Camel components is available.

The Camel Catalog can then be used by tooling to present the list of components to developers.

### Nexus

The Nexus repository must provide a REST API which allows this module to query it.

The url is configured on `ComponentCatalogNexusRepository` in the `nexusUrl` setter.

For example if you have a nexus repository at: `http://company-nexus` then the url can be set as

    http://company-nexus/service/local/data_index
    
The nexus repository is periodically scanner (once per minute by default).
    
    
