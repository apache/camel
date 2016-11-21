-- this is a comment
select *
from projects
where project in (:#in:names)
and license in (:#in:licenses)
order by id