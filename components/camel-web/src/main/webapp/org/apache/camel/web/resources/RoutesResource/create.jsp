<html>
<head>
  <script type='text/javascript' src="<c:url value='/js/dojo/dojo.js'/>"></script>
  <script type='text/javascript' src="<c:url value='/js/bespin/editor/embed.js'/>"></script>
  <script type='text/javascript' src="<c:url value='/js/route.js'/>"></script>

  <title>Create New Route</title>
</head>
<body>
<form id="routeForm" action="<c:url value="/routes"/>" method="post">
<table>
  <tr>
    <td>
      <h2>Create New Route</h2>
    </td>
    <td>
      <input type="submit" value="Save">&nbsp; as &nbsp;<select
			id="language" name="language">
			<option value="Xml" selected>Xml</option>
			<option value="Groovy">Groovy</option>
			<option value="Ruby">Ruby</option>
			<option value="Scala">Scala</option>
		</select>
    </td>
  </tr>
  <tr>
    <td colspan="2"><textarea id="route" name="route"
			onchange="dojo.byId('edited').value = true;"
			style="width: 800px; height: 300px; border: 10px solid #ddd; -moz-border-radius: 10px; -webkit-border-radius: 10px;">
<route xmlns="http://camel.apache.org/schema/spring">
  <description>This is an example route.</description>
  <from uri="seda:Some.Endpoint"/>
  <to uri="seda:Some.Other.Endpoint"/>
</route>
			</textarea>
    </td>
  </tr>
</table>

<div class="error">${it.error}</div>

</form>

<!--

<form id="routeForm" action="<c:url value="/routes"/>" method="post">
<table>
  <tr>
    <td>
      <h2>Create New Route</h2>
    </td>
    <td>
      <input type="button" value="Save" onclick="submitRoute()">
    </td>
    <td>
      <textarea id="route" name="route" rows="1" cols="1"  style="visibility: hidden;"></textarea>
    </td>
  </tr>
</table>

<div class="error">${it.error}</div>

<div id="editor"
     style="height: 300px; border: 10px solid #ddd; -moz-border-radius: 10px; -webkit-border-radius: 10px;">
<route xmlns="http://camel.apache.org/schema/spring">
  <from uri="seda:Some.Endpoint"/>
  <to uri="seda:Some.Other.Endpoint"/>
</route>
</div>

</form>

-->

</body>
</html>
