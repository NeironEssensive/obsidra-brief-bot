create table leads
(
    id           bigint generated always as identity (start with 1001) primary key,
    tg_user_id   bigint       not null,
    tg_username  varchar(64),
    client_name  varchar(128),
    project_type varchar(32)  not null,
    description  text         not null,
    assets       text[]       not null default '{}',
    budget       varchar(64)  not null,
    deadline     varchar(32)  not null,
    contact      varchar(255) not null,
    status       varchar(32)  not null default 'NEW',
    created_at   timestamptz  not null,
    updated_at   timestamptz  not null
);

-- лимит заявок в сутки считается по пользователю и времени
create index leads_tg_user_created_idx on leads (tg_user_id, created_at);
-- /leads и /stats идут от свежих заявок
create index leads_created_idx on leads (created_at desc);
