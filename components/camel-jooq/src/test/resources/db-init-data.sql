--
-- Licensed to the Apache Software Foundation (ASF) under one or more
-- contributor license agreements.  See the NOTICE file distributed with
-- this work for additional information regarding copyright ownership.
-- The ASF licenses this file to You under the Apache License, Version 2.0
-- (the "License"); you may not use this file except in compliance with
-- the License.  You may obtain a copy of the License at
--
--      http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

/* This script is taken from JOOQ examples. */

INSERT INTO author VALUES (next value for s_author_id, 'George', 'Orwell', '1903-06-25', 1903, null);
INSERT INTO author VALUES (next value for s_author_id, 'Paulo', 'Coelho', '1947-08-24', 1947, null);

INSERT INTO book VALUES (1, 1, null, null, '1984', 1948, 1, 'To know and not to know, to be conscious of complete truthfulness while telling carefully constructed lies, to hold simultaneously two opinions which cancelled out, knowing them to be contradictory and believing in both of them, to use logic against logic, to repudiate morality while laying claim to it, to believe that democracy was impossible and that the Party was the guardian of democracy, to forget, whatever it was necessary to forget, then to draw it back into memory again at the moment when it was needed, and then promptly to forget it again, and above all, to apply the same process to the process itself -- that was the ultimate subtlety; consciously to induce unconsciousness, and then, once again, to become unconscious of the act of hypnosis you had just performed. Even to understand the word ''doublethink'' involved the use of doublethink..', null, 1, '2010-01-01 00:00:00');
INSERT INTO book VALUES (2, 1, null, null, 'Animal Farm', 1945, 1, null, null, null, '2010-01-01 00:00:00');
INSERT INTO book VALUES (3, 2, null, null, 'O Alquimista', 1988, 4, null, null, 1, null);
INSERT INTO book VALUES (4, 2, null, null, 'Brida', 1990, 2, null, null, null, null);

INSERT INTO book_store (name) VALUES
('Orell Füssli'),
('Ex Libris'),
('Buchhandlung im Volkshaus');

INSERT INTO book_to_book_store VALUES
('Orell Füssli', 1, 10),
('Orell Füssli', 2, 10),
('Orell Füssli', 3, 10),
('Ex Libris', 1, 1),
('Ex Libris', 3, 2),
('Buchhandlung im Volkshaus', 3, 1);
