Dynamic Endpoint Component
==========================

Provides a component that can install a dynamic number of consumers.

Example:

from("dynamicep:file:/test,jetty:http://localhost:9000").to("log:test");

This route listens on both file:/test as well as jetty:http://localhost:900. Any messages coming in will be forwarded to the rest of the route.
As the list of endpoints can come from a configuration this allows to configure at runtime on which endpoints the route should listen.
 