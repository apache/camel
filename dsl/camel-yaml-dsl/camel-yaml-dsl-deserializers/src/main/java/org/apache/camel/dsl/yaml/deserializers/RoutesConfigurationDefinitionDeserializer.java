package org.apache.camel.dsl.yaml.deserializers;

import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.dsl.yaml.common.YamlDeserializerBase;
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.RoutesConfigurationDefinition;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.annotations.YamlProperty;
import org.apache.camel.spi.annotations.YamlType;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.NodeType;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

import java.util.ArrayList;
import java.util.List;

@YamlType(
          inline = true,
          types = org.apache.camel.model.RoutesConfigurationDefinition.class,
          order = YamlDeserializerResolver.ORDER_DEFAULT,
          nodes = "routes-configuration",
          properties = {
                  @YamlProperty(name = "intercept", type = "array:org.apache.camel.model.InterceptDefinition"),
                  @YamlProperty(name = "intercept-from", type = "array:org.apache.camel.model.InterceptFromDefinition"),
                  @YamlProperty(name = "intercept-send-to-endpoint",
                                type = "array:org.apache.camel.model.InterceptSendToEndpointDefinition"),
                  @YamlProperty(name = "on-completion", type = "array:org.apache.camel.model.OnCompletionDefinition"),
                  @YamlProperty(name = "on-exception", type = "array:org.apache.camel.model.OnExceptionDefinition")
          })
public class RoutesConfigurationDefinitionDeserializer extends YamlDeserializerBase<RoutesConfigurationDefinition> {
    public RoutesConfigurationDefinitionDeserializer() {
        super(RoutesConfigurationDefinition.class);
    }

    @Override
    protected RoutesConfigurationDefinition newInstance() {
        return new RoutesConfigurationDefinition();
    }

    @Override
    public Object construct(Node node) {
        final RoutesConfigurationDefinition target = newInstance();

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
