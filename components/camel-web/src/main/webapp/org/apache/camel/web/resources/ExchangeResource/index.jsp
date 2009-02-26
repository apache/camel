<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <title>Exchange ${it.exchangeId}</title>
</head>
<body>

<h1>Exchange: ${it.exchangeId}</h1>


<table>
  <tr>
    <td valign="top">
      <table>
        <tr>
          <th colspan="2">Properties</th>
        </tr>
        <tr>
          <th>Name</th>
          <th>Value</th>
        </tr>
        <c:forEach items="${it.properties}" var="entry">
          <tr>
            <td>${entry.key}</td>
            <td><a class="type" title="${entry.value.class.canonicalName}">${entry.value}</a></td>
          </tr>
        </c:forEach>
      </table>
    </td>
    <td valign="top">
      <table>
        <tr>
          <th colspan="2">Headers</th>
        </tr>
        <tr>
          <th>Name</th>
          <th>Value</th>
        </tr>
        <c:forEach items="${it.headers}" var="entry">
          <tr>
            <td>${entry.key}</td>
            <td><a class="type" title="${entry.value.class.canonicalName}">${entry.value}</a></td>
          </tr>
        </c:forEach>
      </table>
    </td>
  </tr>
  <tr>
    <th colspan="2">
      Message Body is Java Class: ${it.in.body.class.canonicalName}
    </th>
  </tr>
  <tr>
    <td colspan="2">
      <div class="message"><c:out value="${it.in.body}" escapeXml="true" /></div>
    </td>
  </tr>
</table>
</body>
</html>
