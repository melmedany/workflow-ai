SET TIME ZONE 'UTC';

CREATE TABLE tasks.jobrunr_migrations
(
    id          nchar(36) PRIMARY KEY,
    script      varchar(64) NOT NULL,
    installedOn varchar(29) NOT NULL
);

CREATE TABLE tasks.jobrunr_jobs
(
    id           NCHAR(36) PRIMARY KEY,
    version      int          NOT NULL,
    jobAsJson    text         NOT NULL,
    jobSignature VARCHAR(512) NOT NULL,
    state        VARCHAR(36)  NOT NULL,
    createdAt    TIMESTAMP    NOT NULL,
    updatedAt    TIMESTAMP    NOT NULL,
    scheduledAt  TIMESTAMP
);
CREATE INDEX jobrunr_state_idx ON tasks.jobrunr_jobs (state);
CREATE INDEX jobrunr_job_signature_idx ON tasks.jobrunr_jobs (jobSignature);
CREATE INDEX jobrunr_job_created_at_idx ON tasks.jobrunr_jobs (createdAt);
CREATE INDEX jobrunr_job_updated_at_idx ON tasks.jobrunr_jobs (updatedAt);
CREATE INDEX jobrunr_job_scheduled_at_idx ON tasks.jobrunr_jobs (scheduledAt);

CREATE TABLE tasks.jobrunr_recurring_jobs
(
    id        NCHAR(128) PRIMARY KEY,
    version   int  NOT NULL,
    jobAsJson text NOT NULL
);

CREATE TABLE tasks.jobrunr_backgroundjobservers
(
    id                     NCHAR(36) PRIMARY KEY,
    workerPoolSize         int           NOT NULL,
    pollIntervalInSeconds  int           NOT NULL,
    firstHeartbeat         TIMESTAMP(6)  NOT NULL,
    lastHeartbeat          TIMESTAMP(6)  NOT NULL,
    running                int           NOT NULL,
    systemTotalMemory      BIGINT        NOT NULL,
    systemFreeMemory       BIGINT        NOT NULL,
    systemCpuLoad          NUMERIC(3, 2) NOT NULL,
    processMaxMemory       BIGINT        NOT NULL,
    processFreeMemory      BIGINT        NOT NULL,
    processAllocatedMemory BIGINT        NOT NULL,
    processCpuLoad         NUMERIC(3, 2) NOT NULL
);
CREATE INDEX jobrunr_bgjobsrvrs_fsthb_idx ON tasks.jobrunr_backgroundjobservers (firstHeartbeat);
CREATE INDEX jobrunr_bgjobsrvrs_lsthb_idx ON tasks.jobrunr_backgroundjobservers (lastHeartbeat);

CREATE TABLE tasks.jobrunr_job_counters
(
    name   NCHAR(36) PRIMARY KEY,
    amount int NOT NULL
);

INSERT INTO tasks.jobrunr_job_counters (name, amount)
VALUES ('AWAITING', 0);
INSERT INTO tasks.jobrunr_job_counters (name, amount)
VALUES ('SCHEDULED', 0);
INSERT INTO tasks.jobrunr_job_counters (name, amount)
VALUES ('ENQUEUED', 0);
INSERT INTO tasks.jobrunr_job_counters (name, amount)
VALUES ('PROCESSING', 0);
INSERT INTO tasks.jobrunr_job_counters (name, amount)
VALUES ('FAILED', 0);
INSERT INTO tasks.jobrunr_job_counters (name, amount)
VALUES ('SUCCEEDED', 0);

CREATE VIEW tasks.jobrunr_jobs_stats
AS
SELECT count(*)                                                                                 AS total,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'AWAITING')             AS awaiting,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'SCHEDULED')            AS scheduled,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'ENQUEUED')             AS enqueued,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'PROCESSING')           AS processing,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'FAILED')               AS failed,
       (SELECT((SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'SUCCEEDED') +
               (SELECT amount FROM tasks.jobrunr_job_counters jc WHERE jc.name = 'SUCCEEDED'))) AS succeeded,
       (SELECT count(*) FROM tasks.jobrunr_backgroundjobservers)                                AS nbrOfBackgroundJobServers,
       (SELECT count(*) FROM tasks.jobrunr_recurring_jobs)                                      AS nbrOfRecurringJobs
FROM tasks.jobrunr_jobs j;


DROP VIEW tasks.jobrunr_jobs_stats;

CREATE VIEW tasks.jobrunr_jobs_stats
AS
SELECT count(*)                                                                                 AS total,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'AWAITING')             AS awaiting,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'SCHEDULED')            AS scheduled,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'ENQUEUED')             AS enqueued,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'PROCESSING')           AS processing,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'FAILED')               AS failed,
       (SELECT((SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'SUCCEEDED') +
               (SELECT amount FROM tasks.jobrunr_job_counters jc WHERE jc.name = 'SUCCEEDED'))) AS succeeded,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'DELETED')              AS deleted,
       (SELECT count(*) FROM tasks.jobrunr_backgroundjobservers)                                AS nbrOfBackgroundJobServers,
       (SELECT count(*) FROM tasks.jobrunr_recurring_jobs)                                      AS nbrOfRecurringJobs
FROM tasks.jobrunr_jobs j;

ALTER TABLE tasks.jobrunr_jobs
    ADD recurringJobId VARCHAR(128);
CREATE INDEX jobrunr_job_rci_idx ON tasks.jobrunr_jobs (recurringJobId);

ALTER TABLE tasks.jobrunr_backgroundjobservers
    ADD deleteSucceededJobsAfter VARCHAR(32);
ALTER TABLE tasks.jobrunr_backgroundjobservers
    ADD permanentlyDeleteJobsAfter VARCHAR(32);

CREATE TABLE tasks.jobrunr_metadata
(
    id        varchar(156) PRIMARY KEY,
    name      varchar(92) NOT NULL,
    owner     varchar(64) NOT NULL,
    value     text        NOT NULL,
    createdAt TIMESTAMP   NOT NULL,
    updatedAt TIMESTAMP   NOT NULL
);

INSERT INTO tasks.jobrunr_metadata (id, name, owner, value, createdAt, updatedAt)
VALUES ('succeeded-jobs-counter-cluster', 'succeeded-jobs-counter', 'cluster',
        cast((SELECT amount FROM tasks.jobrunr_job_counters WHERE name = 'SUCCEEDED') AS char(10)), CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

DROP VIEW tasks.jobrunr_jobs_stats;
DROP TABLE tasks.jobrunr_job_counters;

CREATE VIEW tasks.jobrunr_jobs_stats
AS
SELECT count(*)                                                                       AS total,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'AWAITING')   AS awaiting,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'SCHEDULED')  AS scheduled,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'ENQUEUED')   AS enqueued,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'PROCESSING') AS processing,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'FAILED')     AS failed,
       (SELECT((SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'SUCCEEDED') +
               (SELECT cast(cast(value AS char(10)) AS decimal(10, 0))
                FROM tasks.jobrunr_metadata jm
                WHERE jm.id = 'succeeded-jobs-counter-cluster')))                     AS succeeded,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'DELETED')    AS deleted,
       (SELECT count(*) FROM tasks.jobrunr_backgroundjobservers)                      AS nbrOfBackgroundJobServers,
       (SELECT count(*) FROM tasks.jobrunr_recurring_jobs)                            AS nbrOfRecurringJobs
FROM tasks.jobrunr_jobs j;

DROP VIEW tasks.jobrunr_jobs_stats;

CREATE VIEW tasks.jobrunr_jobs_stats
AS
SELECT count(*)                                                                       AS total,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'AWAITING')   AS awaiting,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'SCHEDULED')  AS scheduled,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'ENQUEUED')   AS enqueued,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'PROCESSING') AS processing,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'FAILED')     AS failed,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'SUCCEEDED')  AS succeeded,
       (SELECT cast(cast(value AS char(10)) AS decimal(10, 0))
        FROM tasks.jobrunr_metadata jm
        WHERE jm.id = 'succeeded-jobs-counter-cluster')                               AS allTimeSucceeded,
       (SELECT count(*) FROM tasks.jobrunr_jobs jobs WHERE jobs.state = 'DELETED')    AS deleted,
       (SELECT count(*) FROM tasks.jobrunr_backgroundjobservers)                      AS nbrOfBackgroundJobServers,
       (SELECT count(*) FROM tasks.jobrunr_recurring_jobs)                            AS nbrOfRecurringJobs
FROM tasks.jobrunr_jobs j;

ALTER TABLE tasks.jobrunr_recurring_jobs
    ADD createdAt BIGINT NOT NULL DEFAULT '0';
CREATE INDEX jobrunr_recurring_job_created_at_idx ON tasks.jobrunr_recurring_jobs (createdAt);

DROP VIEW tasks.jobrunr_jobs_stats;
CREATE VIEW tasks.jobrunr_jobs_stats
AS
with job_stat_results AS (SELECT state, count(*) AS count
                          FROM tasks.jobrunr_jobs
                          GROUP BY ROLLUP (state
                              ))
SELECT coalesce((SELECT count FROM job_stat_results WHERE state IS NULL), 0)        AS total,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'SCHEDULED'), 0)  AS scheduled,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'ENQUEUED'), 0)   AS enqueued,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'PROCESSING'), 0) AS processing,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'FAILED'), 0)     AS failed,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'SUCCEEDED'), 0)  AS succeeded,
       coalesce((SELECT cast(cast(value AS char(10)) AS decimal(10, 0))
                 FROM tasks.jobrunr_metadata jm
                 WHERE jm.id = 'succeeded-jobs-counter-cluster'), 0)                AS allTimeSucceeded,
       coalesce((SELECT count FROM job_stat_results WHERE state = 'DELETED'), 0)    AS deleted,
       (SELECT count(*) FROM tasks.jobrunr_backgroundjobservers)                    AS nbrOfBackgroundJobServers,
       (SELECT count(*) FROM tasks.jobrunr_recurring_jobs)                          AS nbrOfRecurringJobs;

DROP INDEX tasks.jobrunr_job_updated_at_idx;
CREATE INDEX jobrunr_jobs_state_updated_idx ON tasks.jobrunr_jobs (state ASC, updatedAt ASC);

ALTER TABLE tasks.jobrunr_backgroundjobservers
    ADD name VARCHAR(128);

DROP VIEW tasks.jobrunr_jobs_stats;
CREATE VIEW tasks.jobrunr_jobs_stats
AS
with job_stat_results AS (SELECT state, count(*) AS count
                          FROM tasks.jobrunr_jobs
                          GROUP BY state)
SELECT coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results), 0)                            AS total,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'AWAITING'), 0)   AS awaiting,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'SCHEDULED'), 0)  AS scheduled,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'ENQUEUED'), 0)   AS enqueued,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'PROCESSING'), 0) AS processing,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'PROCESSED'), 0)  AS processed,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'FAILED'), 0)     AS failed,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'SUCCEEDED'), 0)  AS succeeded,
       coalesce((SELECT cASt(cASt(value AS char(10)) AS decimal(10, 0))
                 FROM tasks.jobrunr_metadata jm
                 WHERE jm.id = 'succeeded-jobs-counter-cluster'),
                0)                                                                                        AS allTimeSucceeded,
       coalesce((SELECT sum(job_stat_results.count) FROM job_stat_results WHERE state = 'DELETED'), 0)    AS deleted,
       (SELECT count(*) FROM tasks.jobrunr_backgroundjobservers)                                          AS nbrOfBackgroundJobServers,
       (SELECT count(*) FROM tasks.jobrunr_recurring_jobs)                                                AS nbrOfRecurringJobs;