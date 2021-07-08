package org.apache.camel.dsl.yaml.deserializers;

import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializerBase;
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

@YamlType(
          inline = true,
          types = org.apache.camel.model.RouteConfigurationDefinition.class,
          order = YamlDeserializerResolver.ORDER_DEFAULT,
          nodes = "route-configuration",
          properties = {
                  @YamlProperty(name = "intercept", type = "array:org.apache.camel.model.InterceptDefinition"),
                  @YamlProperty(name = "intercept-from", type = "array:org.apache.camel.model.InterceptFromDefinition"),
                  @YamlProperty(name = "intercept-send-to-endpoint",
                                type = "array:org.apache.camel.model.InterceptSendToEndpointDefinition"),
                  @YamlProperty(name = "on-completion", type = "array:org.apache.camel.model.OnCompletionDefinition"),
                  @YamlProperty(name = "on-exception", type = "array:org.apache.camel.model.OnExceptionDefinition")
          })
public class RouteConfigurationDefinitionDeserializer extends YamlDeserializerBase<RouteConfigurationDefinition> {
    public RouteConfigurationDefinitionDeserializer() {
        super(RouteConfigurationDefinition.class);
    }

    @Override
    protected RouteConfigurationDefinition newInstance() {
        return new RouteConfigurationDefinition();
    }

    @Override
    public Object construct(Node node) {
        final RouteConfigurationDefinition target = newInstance();

        final YamlDeserializationContext dc = getDeserializationContext(node);
        final SequenceNode sn = asSequenceNode(node);
        for (Node item : sn.getValue()) {
            final MappingNode bn = asMappingNode(item);
            setDeserializationContext(item, dc);

            for (NodeTuple tuple : bn.getValue()) {
                final String key = asText(tuple.getKeyNode());
                final Node val = tuple.getValueNode();
                switch (key) {
                    case "on-exception":
                        setDeserializationContext(val, dc);
                        OnExceptionDefinition obj = asType(val, OnExceptionDefinition.class);
                        target.getOnExceptions().add(obj);
                        break;
                }
            }
        }

        return target;
    }

}
