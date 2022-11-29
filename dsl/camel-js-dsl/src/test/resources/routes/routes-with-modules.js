import { greeting } from "src/test/resources/module.mjs";

from('timer:tick')
    .to('log:info')