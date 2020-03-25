#bin/sh

#version: 2.x component: components file: undefined path modules/ROOT/pages/bam.adoc"` lineno: undefined message: manual::correlation-identifier
sed -i -e 's/xref:manual::correlation-identifier/xref:{eip-vc}:eips:correlation-identifier/g' `find . -name "bam.adoc"`
#version: 2.x component: components file: undefined path modules/ROOT/pages/beanstalk-component.adoc"` lineno: undefined message: latest@manual::polling-consumer
sed -i -e 's/xref:latest@manual::polling-consumer/xref:{eip-vc}:eips:polling-consumer/g' `find . -name "beanstalk-component.adoc"`
#version: 2.x component: components file: undefined path modules/ROOT/pages/facebook-component.adoc"` lineno: undefined message: latest@manual::polling-consumer
sed -i -e 's/xref:latest@manual::polling-consumer/xref:{eip-vc}:eips:polling-consumer/g' `find . -name "facebook-component.adoc"`
#version: 2.x component: components file: undefined path modules/ROOT/pages/jms-component.adoc"` lineno: undefined message: latest@manual::dead-letter-channel
sed -i -e 's/xref:latest@manual::dead-letter-channel/xref:{eip-vc}:eips:dead-letter-channel/g' `find . -name "jms-component.adoc"`
#version: 2.x component: components file: undefined path modules/ROOT/pages/jpa-component.adoc"` lineno: undefined message: latest@manual::enterprise-integration-patterns
sed -i -e 's/xref:latest@manual::enterprise-integration-patterns/xref:{eip-vc}:eips:enterprise-integration-patterns/g' `find . -name "jpa-component.adoc"`
#version: 2.x component: components file: undefined path modules/ROOT/pages/quartz-component.adoc"` lineno: undefined message: latest@manual::polling-consumer
sed -i -e 's/xref:latest@manual::polling-consumer/xref:{eip-vc}:eips:polling-consumer/g' `find . -name "quartz-component.adoc"`
#version: 2.x component: components file: undefined path modules/ROOT/pages/quartz2-component.adoc"` lineno: undefined message: latest@manual::polling-consumer
sed -i -e 's/xref:latest@manual::polling-consumer/xref:{eip-vc}:eips:polling-consumer/g' `find . -name "quartz2-component.adoc"`
#version: 2.x component: components file: undefined path modules/ROOT/pages/spring-event-component.adoc"` lineno: undefined message: latest@manual::enterprise-integration-patterns
sed -i -e 's/xref:latest@manual::enterprise-integration-patterns/xref:{eip-vc}:eips:enterprise-integration-patterns/g' `find . -name "spring-event-component.adoc"`
#version: 2.x component: components file: undefined path modules/ROOT/pages/spring.adoc"` lineno: undefined message: latest@manual::enterprise-integration-patterns
sed -i -e 's/xref:latest@manual::enterprise-integration-patterns/xref:{eip-vc}:eips:enterprise-integration-patterns/g' `find . -name "spring.adoc"`
#version: 2.x component: components file: undefined path modules/ROOT/pages/test-spring.adoc"` lineno: undefined message: latest@manual::enterprise-integration-patterns
sed -i -e 's/xref:latest@manual::enterprise-integration-patterns/xref:{eip-vc}:eips:enterprise-integration-patterns/g' `find . -name "test-spring.adoc"`
#version: 2.x component: components file: undefined path modules/ROOT/pages/test.adoc"` lineno: undefined message: latest@manual::enterprise-integration-patterns
sed -i -e 's/xref:latest@manual::enterprise-integration-patterns/xref:{eip-vc}:eips:enterprise-integration-patterns/g' `find . -name "test.adoc"`
