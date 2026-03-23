-- Remote Panel - PostgreSQL schema
-- Multi-tenant design for managing many bot sessions from one panel.

create extension if not exists pgcrypto;

create table if not exists app_user (
    id uuid primary key default gen_random_uuid(),
    email text not null unique,
    password_hash text not null,
    display_name text not null,
    role text not null default 'CUSTOMER',
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists workspace (
    id uuid primary key default gen_random_uuid(),
    owner_user_id uuid not null references app_user(id),
    name text not null,
    status text not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists workspace_member (
    workspace_id uuid not null references workspace(id) on delete cascade,
    user_id uuid not null references app_user(id) on delete cascade,
    member_role text not null default 'VIEWER',
    created_at timestamptz not null default now(),
    primary key (workspace_id, user_id)
);

create table if not exists bot_license (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspace(id) on delete cascade,
    license_key text not null unique,
    plan_code text not null,
    max_concurrent_slots int not null default 1,
    starts_at timestamptz not null,
    expires_at timestamptz not null,
    status text not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists bot_instance (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references workspace(id) on delete cascade,
    license_id uuid references bot_license(id),
    external_bot_id text not null,
    label text not null,
    assigned_node text,
    status text not null default 'OFFLINE',
    last_seen_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (workspace_id, external_bot_id)
);

create index if not exists idx_bot_instance_workspace on bot_instance(workspace_id);
create index if not exists idx_bot_instance_status on bot_instance(status);

create table if not exists bot_session (
    id uuid primary key default gen_random_uuid(),
    bot_instance_id uuid not null references bot_instance(id) on delete cascade,
    session_token_hash text not null,
    ip_address inet,
    user_agent text,
    started_at timestamptz not null default now(),
    ended_at timestamptz,
    status text not null default 'OPEN'
);

create index if not exists idx_bot_session_bot on bot_session(bot_instance_id, status);

create table if not exists bot_command (
    id uuid primary key default gen_random_uuid(),
    bot_instance_id uuid not null references bot_instance(id) on delete cascade,
    requested_by_user_id uuid references app_user(id),
    action text not null,
    parameter text,
    request_id text,
    status text not null default 'QUEUED',
    queued_at timestamptz not null default now(),
    delivered_at timestamptz,
    executed_at timestamptz,
    error_message text
);

create index if not exists idx_bot_command_queue on bot_command(bot_instance_id, status, queued_at);

create table if not exists bot_telemetry (
    id bigserial primary key,
    bot_instance_id uuid not null references bot_instance(id) on delete cascade,
    source_tick bigint,
    running boolean,
    map_id int,
    map_name text,
    module_id text,
    hp_percent numeric(5,4),
    shield_percent numeric(5,4),
    cargo_percent numeric(5,4),
    raw_payload jsonb not null,
    received_at timestamptz not null default now()
);

create index if not exists idx_bot_telemetry_bot_time on bot_telemetry(bot_instance_id, received_at desc);
create index if not exists idx_bot_telemetry_payload_gin on bot_telemetry using gin (raw_payload);

create table if not exists audit_log (
    id bigserial primary key,
    workspace_id uuid references workspace(id) on delete cascade,
    actor_user_id uuid references app_user(id),
    target_type text not null,
    target_id text not null,
    event_type text not null,
    payload jsonb,
    created_at timestamptz not null default now()
);

create index if not exists idx_audit_workspace_time on audit_log(workspace_id, created_at desc);

