Camel Slack Component
=====================

The **slack** component allows you to connect to an instance of [Slack](http://www.slack.com) and delivers a message contained in the message body via a pre established [Slack incoming webhook](https://api.slack.com/incoming-webhooks).

## URI format

To send a message to a channel.

```
slack:#channel[?options]
```

To send a direct message to a slackuser.

```
slack:@username[?options]
```

The Slack component only supports producer endpoints so you cannot use this component at the beginning of a route to listen to messages in a channel.

## Options

These options will 

| Option | Example | Description |
| ------ | ------- | ----------- |
| `username` | `username=CamelUser` | This is the username that the bot will have when sending messages to a channel or user. |
| `iconUrl` | `iconUrl=http://somehost.com/avatar.gif` | The avatar that the component will use when sending message to a channel or user. |
| `iconEmoji` | `iconEmoji=:camel:` | Use a Slack emoji as an avatar |

## SlackComponent

The SlackComponent must be configured as a Spring or Blueprint bean that contains the incoming webhook url for the integration as a parameter.

```
<bean id="slack" class="org.apache.camel.component.SlackComponent">
    <property name="webhookUrl" value="https://hooks.slack.com/services/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"/>
</bean>
```

## Example Configuration

```
<?xml version="1.0" encoding="UTF-8"?>
<blueprint xmlns="http://www.osgi.org/xmlns/blueprint/v1.0.0" default-activation="lazy">

    <bean id="slack" class="org.apache.camel.component.SlackComponent">
        <property name="webhookUrl" value="https://hooks.slack.com/services/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"/>
    </bean>
    
    <camelContext xmlns="http://camel.apache.org/schema/blueprint">
        <route>
            <from uri="direct:test"/>
            <to uri="slack:#channel?iconEmoji=:camel:&amp;username=CamelTest"/>
        </route>
    </camelContext>

</blueprint>
```