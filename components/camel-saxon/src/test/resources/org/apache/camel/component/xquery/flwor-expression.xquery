<base>
{
	for $item in /items/item
	let $idname := concat($item/id, $item/name)
	where $idname != '2second'
	order by $item/id
	return $item/id
}
</base>