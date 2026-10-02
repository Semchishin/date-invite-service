create table invitation
(
    id           uuid primary key,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    completed_at timestamptz
);

comment on table invitation is 'Приглашение: создаётся при первом открытии страницы, ответы сюда не пишутся';
comment on column invitation.id is 'Идентификатор приглашения, лежит и в сессии';
comment on column invitation.created_at is 'Когда приглашение создано';
comment on column invitation.updated_at is 'Когда приглашение менялось в последний раз';
comment on column invitation.completed_at is 'Когда приглашение подтвердили, до этого null';

create table invitation_answer
(
    id            bigserial primary key,
    invitation_id uuid         not null references invitation (id) on delete cascade,
    step          varchar(32)  not null,
    answer_value  varchar(255) not null,
    note          text,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    constraint uq_invitation_answer_step unique (invitation_id, step)
);

comment on table invitation_answer is 'Ответы приглашения: по одному на каждый шаг сценария';
comment on column invitation_answer.step is 'Шаг сценария: agree, place, date или time';
comment on column invitation_answer.answer_value is 'Выбранный вариант: идентификатор места, дата, время или yes';
comment on column invitation_answer.note is 'Свой вариант места, если вместо готового выбран свой';

create index idx_invitation_created_at on invitation (created_at desc);
