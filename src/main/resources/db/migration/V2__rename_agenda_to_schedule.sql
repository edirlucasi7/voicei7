ALTER TABLE agenda RENAME TO schedule;
ALTER TABLE schedule RENAME CONSTRAINT agenda_pkey TO schedule_pkey;
