# Metrics Component

**Available as of Camel 2.13**

The **metrics:** component allows you to collect various metrics directly from Camel routes. Supported metric types are _counter_, _meter_, _histogram_ and _timer_. [Metrics](http://metrics.codahale.com) provides simple way to measure behaviour of your application. Configurable reporting _backend_ is enabling different integration options for collecting and visualizing statistics. 

Maven users will need to add the following dependency to their pom.xml for this component:

```xml

<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-metrics</artifactId>
    <version>x.x.x</version>
    <!-- use the same version as your Camel core version -->
</dependency>
```

# URI format

```
metrics:[ meter | counter | histogram | timer ]:metricname[?options]
```

# Metric Registry

If MetricRegistry instance for name ```metricRegistry``` is not found from Camel registry default one is used. Default MetricRegistry uses Slf4jReporter and 60 second reporting interval.
MetricRegistry instance can be configured by adding bean with name ```metricRegistry``` to Camel registry. For example using Spring Java Configuration.

```java

    @Configuration
    public static class MyConfig extends SingleRouteCamelConfiguration {

        @Bean
        @Override
        public RouteBuilder route() {
            return new RouteBuilder() {

                @Override
                public void configure() throws Exception {
                    // define Camel routes here
                }
            };
        }

        @Bean(name = MetricsComponent.METRIC_REGISTRY_NAME)
        public MetricRegistry getMetricRegistry() {
            MetricRegistry registry = ...;
            return registry;
        }
    }

```

# Usage

Each metric has type and name. Supported types are ```counter```, ```meter```, ```histogram``` and ```timer```. Metric name is simple string. If metric type is not provided then type ```meter``` is used by default.

## Metrics type counter

```
metrics:counter:metricname[?options]
```

Where options are

| Name      | Default | Description                             |
|-----------|---------|-----------------------------------------|
| increment |         | long value to add to the counter        |
| decrement |         | long value to subtract from the counter |

If neither ```increment``` or ```decrement``` is defined counter value will be incremented by one. If ```increment``` and ```decrement``` are both defined only increment operation is called.

```java
// update counter simple.counter by 7
from("direct:in").to("metric:counter:simple.counter?increment=7").to("direct:out")
```

```java
// increment counter simple.counter by 1
from("direct:in").to("metric:counter:simple.counter").to("direct:out")
```

```java
// decrement counter simple.counter by 3
from("direct:in").to("metric:counter:simple.counter?decrement=3").to("direct:out")
```

## Metric type meter

```
metrics:meter:metricname[?options]
```

Where options are

| Name | Default | Description               |
|------|---------|---------------------------|
| mark |         | long value to use as mark |

If ```mark``` is not set ```meter.mark()``` is called without argument.

```java
// marks simple.meter without value
from("direct:in").to("metric:simple.meter").to("direct:out")
```

```java
// marks simple.meter with value 81
from("direct:in").to("metric:meter:simple.meter?mark=81").to("direct:out")
```

## Metric type histogram

```
metrics:histogram:metricname[?options]
```

Where options are

| Name  | Default | Description               |
|-------|---------|---------------------------|
| value |         | value to use in histogram |

If no ```value``` is not set nothing is added to histogram and warning is logged.

```java
// adds value 9923 to simple.histogram
from("direct:in").to("metric:histogram:simple.histogram?value=9923").to("direct:out")
```

```java
// nothing is added to simple.histogram; warning is logged
from("direct:in").to("metric:histogram:simple.histogram").to("direct:out")
```

## Metrics type timer

```
metrics:timer:metricname[?options]
```

Where options are

| Name   | Default | Description               |
|--------|---------|---------------------------|
| action |         | ```start``` or ```stop``` |

If no ```action``` or invalid value is provided warning is logged and no timer is updated. If ```action``` ```start``` is called on already running timer or ```stop``` is called on not running timer nothing is updated and warning is logged.

```java
// measure time taken by route calculate
from("direct:in")
    .to("metrics:timer:simple.timer?action=start")
    .to("direct:calculate")
    .to("metrics:timer:simple.timer?action=stop");
```

Timer Context objects are stored as Exchange properties.
