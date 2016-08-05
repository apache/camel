# camel-cm-sms

[Camel component](http://camel.apache.org/components.html) for the [CM SMS Gateway](https://www.cmtelecom.com). 

It allows to integrate [CM SMS API](https://dashboard.onlinesmsgateway.com/docs) in an application as a camel component. 

You must have a valid account.  More information are available at [CM Telecom](https://www.cmtelecom.com/support).

### URI Format

```
cm-sms://sgw01.cm.nl/gateway.ashx?defaultFrom=DefaultSender&defaultMaxNumberOfParts=8&productToken=2fb82162-754c-4c1b-806d-9cb7efd677f4
```


### Endpoint Options

CM SMS endpoints act like a **producer** and support the following options.

| Name  | Default Value | Description |
| ------------- | ------------- | ------------- |
| productToken  |*none* |**Required**. UUID as String. This is the product token that was sent to you by email. Example: 'de7c7df3-81ca-4d1e-863d-95a252120321'|
| defaultFrom  | *none* |**Required**. This is the default sender name, to be included in each SMSMessage instance not providing one. The maximum length is 11 characters.|
| defaultMaxNumberOfParts  | 8 | If it is a [multipart message](https://dashboard.onlinesmsgateway.com/docs#send-a-message-multipart) forces the max number of parts to be sent. <p>The gateway will first check if a message is larger than 160 characters, if so, the message will be cut into multiple 153 characters parts limited by these parameters whether the message is [GSM 0038 encodeable](https://en.wikipedia.org/wiki/GSM_03.38). <p>Otherwise, The gateway will check if a message is larger than 70 characters, if so, the message will be cut into multiple 67 characters parts to a maximum of this parameter.|
| testConnectionOnStartup | false | This ensures that Camel is not started with failed connections cause an exception is thrown on startup. | 

### Sample of Usage
You can try [this project](https://github.com/oalles/camel-cm-sample) to see how camel-cm-sms can be integrated in a camel route. 