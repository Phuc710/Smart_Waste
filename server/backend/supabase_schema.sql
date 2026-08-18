-- SmartWaste IoT & Management Platform — Production Database Schema
-- Default Admin Account: admin / admin123

-- 1. Extensions
create extension if not exists pgcrypto with schema extensions;

-- 2. Utility Functions
create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin
    new.updated_at = clock_timestamp();
    return new;
end;
$$;

-- 3. Employee Accounts
create table if not exists public.employee_accounts (
    id uuid primary key default gen_random_uuid(),
    full_name text not null check (length(trim(full_name)) >= 2),
    username text not null unique check (username ~ '^[a-z0-9._-]{3,32}$'),
    email text,
    auth_user_id uuid references auth.users(id) on delete set null,
    password_hash text not null,
    role text not null default 'staff' check (role in ('admin', 'staff')),
    is_active boolean not null default true,
    deleted_at timestamptz,
    last_login timestamptz,
    created_at timestamptz not null default clock_timestamp(),
    updated_at timestamptz not null default clock_timestamp()
);

create unique index if not exists employee_accounts_email_idx on public.employee_accounts (lower(email)) where email is not null;
create unique index if not exists employee_accounts_auth_user_idx on public.employee_accounts (auth_user_id) where auth_user_id is not null;
create index if not exists employee_accounts_active_idx on public.employee_accounts (is_active) where deleted_at is null;

drop trigger if exists set_employee_accounts_updated_at on public.employee_accounts;
create trigger set_employee_accounts_updated_at
before update on public.employee_accounts
for each row execute function public.set_updated_at();

-- 4. Employee Sessions (8-hour sliding session)
create table if not exists public.employee_sessions (
    token_hash text primary key,
    employee_id uuid not null references public.employee_accounts(id) on delete cascade,
    expires_at timestamptz not null,
    created_at timestamptz not null default clock_timestamp()
);

create index if not exists employee_sessions_employee_idx on public.employee_sessions (employee_id);
create index if not exists employee_sessions_expiry_idx on public.employee_sessions (expires_at);

-- 4b. Admin Initialization Note
-- NOTE: Default static admin seed ('admin' / 'admin123') has been removed for production security.
-- Initial admin account is created safely via the bootstrap script (npm run seed or environment bootstrap).


-- 5. Smart Waste Bins
create table if not exists public.smart_bins (
    device_id text primary key check (device_id ~ '^[A-Za-z0-9_-]{1,64}$'),
    name text not null default 'Thùng rác mới',
    location text not null default 'Chưa cập nhật vị trí',
    state text not null default 'CLOSED' check (state in ('CLOSED', 'CONFIRMING', 'OPEN')),
    control_mode text not null default 'AUTO' check (control_mode in ('AUTO', 'MANUAL')),
    servo_angle smallint not null default 0,
    dist_user numeric(8,2) not null default 0,
    dist_level numeric(8,2) not null default 0,
    level_percent numeric(5,2) not null default 0 check (level_percent between 0 and 100),
    ip_address text,
    is_online boolean not null default false,
    last_command text check (last_command is null or last_command in ('OPEN', 'CLOSE', 'AUTO', 'PAUSE', 'RESUME')),
    last_command_at timestamptz,
    command_status text not null default 'done',
    command_requested_by uuid references public.employee_accounts(id) on delete set null,
    command_processed_at timestamptz,
    collection_status text not null default 'IDLE' check (collection_status in ('IDLE', 'RESERVED', 'IN_PROGRESS', 'PAUSED')),
    collection_employee_id uuid references public.employee_accounts(id) on delete set null,
    collection_employee_name text,
    collection_started_at timestamptz,
    collection_completed_at timestamptz,
    collection_paused boolean not null default false,
    latitude double precision check (latitude is null or latitude between -90 and 90),
    longitude double precision check (longitude is null or longitude between -180 and 180),
    last_seen timestamptz not null default clock_timestamp(),
    created_at timestamptz not null default clock_timestamp(),
    updated_at timestamptz not null default clock_timestamp()
);

create index if not exists smart_bins_level_idx on public.smart_bins (level_percent desc);
create index if not exists smart_bins_collection_status_idx on public.smart_bins (collection_status);
create index if not exists smart_bins_collection_employee_idx on public.smart_bins (collection_employee_id);

drop trigger if exists set_smart_bins_updated_at on public.smart_bins;
create trigger set_smart_bins_updated_at
before update on public.smart_bins
for each row execute function public.set_updated_at();

-- 5b. Dedicated Device Commands Queue (Distributed Queue with Worker Locking)
create table if not exists public.device_commands (
    id uuid primary key default gen_random_uuid(),
    device_id text not null references public.smart_bins(device_id) on delete cascade,
    action text not null check (action in ('OPEN', 'OPEN_LID', 'CLOSE', 'CLOSE_LID', 'AUTO', 'MANUAL', 'PAUSE', 'RESUME')),
    status text not null default 'pending' check (status in ('pending', 'processing', 'sent', 'done', 'failed', 'timeout')),
    attempts integer not null default 0,
    max_attempts integer not null default 3,
    issued_by uuid references public.employee_accounts(id) on delete set null,
    issued_at timestamptz not null default clock_timestamp(),
    expires_at timestamptz not null default (clock_timestamp() + interval '30 seconds'),
    last_attempt_at timestamptz,
    acknowledged_at timestamptz,
    error_message text
);

create index if not exists device_commands_poller_idx on public.device_commands (status, issued_at asc) where status in ('pending', 'processing');
create index if not exists device_commands_device_idx on public.device_commands (device_id, issued_at desc);

-- 6. Bin Events (Telemetry & Alert Log)
create table if not exists public.bin_events (
    id bigint generated by default as identity primary key,
    device_id text not null references public.smart_bins(device_id) on delete cascade,
    event_type text not null check (event_type in ('telemetry', 'command', 'alert')),
    payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default clock_timestamp()
);

create index if not exists bin_events_device_time_idx on public.bin_events (device_id, created_at desc);

-- 7. Employee GPS Locations
create table if not exists public.employee_locations (
    employee_id uuid primary key references public.employee_accounts(id) on delete cascade,
    latitude double precision not null check (latitude between -90 and 90),
    longitude double precision not null check (longitude between -180 and 180),
    accuracy double precision,
    heading double precision,
    speed double precision,
    recorded_at timestamptz not null default clock_timestamp()
);

-- 7b. Employee Location Breadcrumb Points (Tracking Trail History)
create table if not exists public.employee_location_points (
    id bigint generated by default as identity primary key,
    employee_id uuid not null references public.employee_accounts(id) on delete cascade,
    tracking_session_id text,
    job_id text,
    latitude double precision not null check (latitude between -90 and 90),
    longitude double precision not null check (longitude between -180 and 180),
    accuracy double precision,
    heading double precision,
    speed double precision,
    recorded_at timestamptz not null default clock_timestamp(),
    created_at timestamptz not null default clock_timestamp()
);

create index if not exists employee_location_points_session_idx on public.employee_location_points (employee_id, tracking_session_id, recorded_at asc);
create index if not exists employee_location_points_job_idx on public.employee_location_points (job_id, recorded_at asc);


-- 8. Collection Jobs & Items (Route / Batch collection)
create table if not exists public.collection_jobs (
    id text primary key,
    employee_id uuid references public.employee_accounts(id) on delete set null,
    employee_name text not null,
    source text not null check (source in ('ADMIN_ASSIGNED', 'STAFF_SELF_PICK', 'AUTO_SYSTEM')),
    status text not null default 'PENDING' check (status in ('PENDING', 'ASSIGNED', 'ACCEPTED', 'REJECTED', 'IN_PROGRESS', 'PAUSED', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    target_bin_ids text[] not null default '{}',
    route_data jsonb,
    reassigned_from_job text,
    pause_reason text,
    created_at timestamptz not null default clock_timestamp(),
    assigned_at timestamptz,
    accepted_at timestamptz,
    started_at timestamptz,
    paused_at timestamptz,
    completed_at timestamptz,
    cancelled_at timestamptz,
    version integer not null default 1
);

create index if not exists collection_jobs_employee_status_idx on public.collection_jobs (employee_id, status);
create index if not exists collection_jobs_status_idx on public.collection_jobs (status);
create index if not exists collection_jobs_created_idx on public.collection_jobs (created_at desc);

create table if not exists public.job_bin_items (
    id bigint generated by default as identity primary key,
    job_id text not null references public.collection_jobs(id) on delete cascade,
    bin_id text not null references public.smart_bins(device_id) on delete cascade,
    status text not null default 'PENDING' check (status in ('PENDING', 'COLLECTED', 'SKIPPED', 'INCIDENT')),
    collected_at timestamptz,
    note text,
    photo_url text,
    unique (job_id, bin_id)
);

create index if not exists job_bin_items_job_status_idx on public.job_bin_items (job_id, status);
create index if not exists job_bin_items_bin_idx on public.job_bin_items (bin_id);

-- 9. Single Bin Collection History
create table if not exists public.bin_collections (
    id bigint generated by default as identity primary key,
    device_id text not null references public.smart_bins(device_id) on delete cascade,
    employee_id uuid not null references public.employee_accounts(id) on delete restrict,
    employee_name text not null,
    status text not null default 'IN_PROGRESS' check (status in ('IN_PROGRESS', 'COMPLETED')),
    started_at timestamptz not null default clock_timestamp(),
    completed_at timestamptz
);

create unique index if not exists bin_collections_one_active_bin_idx on public.bin_collections (device_id) where status = 'IN_PROGRESS';
create index if not exists bin_collections_device_time_idx on public.bin_collections (device_id, started_at desc);
create index if not exists bin_collections_employee_idx on public.bin_collections (employee_id);

-- 10. Incidents & Photo Storage
create table if not exists public.incident_reports (
    id bigint generated by default as identity primary key,
    device_id text not null references public.smart_bins(device_id) on delete cascade,
    employee_id uuid not null references public.employee_accounts(id) on delete restrict,
    employee_name text not null,
    reason text not null check (length(trim(reason)) between 3 and 120),
    description text not null default '' check (length(description) <= 500),
    has_photo boolean not null default false,
    proof_image_url text,
    status text not null default 'NEW' check (status in ('NEW', 'IN_REVIEW', 'RESOLVED')),
    created_at timestamptz not null default clock_timestamp(),
    resolved_at timestamptz
);

create index if not exists incident_reports_device_idx on public.incident_reports (device_id);
create index if not exists incident_reports_employee_idx on public.incident_reports (employee_id, created_at desc);
create index if not exists incident_reports_status_idx on public.incident_reports (status, created_at desc);

create table if not exists public.incident_image_uploads (
    id uuid primary key default gen_random_uuid(),
    employee_id uuid not null references public.employee_accounts(id) on delete cascade,
    device_id text not null references public.smart_bins(device_id) on delete cascade,
    reason text not null,
    description text not null default '',
    object_path text not null unique,
    expires_at timestamptz not null,
    report_id bigint references public.incident_reports(id) on delete set null,
    completed_at timestamptz,
    created_at timestamptz not null default clock_timestamp()
);

create index if not exists incident_image_uploads_expiry_idx on public.incident_image_uploads (expires_at) where completed_at is null;
create index if not exists incident_image_uploads_employee_idx on public.incident_image_uploads (employee_id);

insert into storage.buckets (id, name, public)
values ('incident-images', 'incident-images', false)
on conflict (id) do update set public = false;

-- 11. Stored Procedures: Auth & User Management
create or replace function public.employee_login(
    p_username text,
    p_password text,
    p_token_hash text
)
returns table (id uuid, username text, full_name text, role text)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_employee public.employee_accounts%rowtype;
begin
    delete from public.employee_sessions where expires_at <= clock_timestamp();

    select * into v_employee
    from public.employee_accounts e
    where e.username = lower(trim(p_username))
      and e.is_active = true
      and e.deleted_at is null
      and e.password_hash = crypt(p_password, e.password_hash);

    if not found then return; end if;

    insert into public.employee_sessions (token_hash, employee_id, expires_at)
    values (p_token_hash, v_employee.id, clock_timestamp() + interval '8 hours')
    on conflict (token_hash) do update set
        employee_id = excluded.employee_id,
        expires_at = excluded.expires_at;

    update public.employee_accounts set last_login = clock_timestamp() where id = v_employee.id;
    return query select v_employee.id, v_employee.username, v_employee.full_name, v_employee.role;
end;
$$;

create or replace function public.employee_current(p_token_hash text)
returns table (id uuid, username text, full_name text, role text)
language sql
stable
security definer
set search_path = public
as $$
    select e.id, e.username, e.full_name, e.role
    from public.employee_sessions s
    join public.employee_accounts e on e.id = s.employee_id
    where s.token_hash = p_token_hash
      and s.expires_at > clock_timestamp()
      and e.is_active = true
      and e.deleted_at is null
    limit 1;
$$;

create or replace function public.employee_logout(p_token_hash text)
returns void
language sql
security definer
set search_path = public
as $$
    delete from public.employee_sessions where token_hash = p_token_hash;
$$;

create or replace function public.employee_list(p_token_hash text)
returns table (
    id uuid,
    username text,
    full_name text,
    role text,
    is_active boolean,
    last_login timestamptz,
    created_at timestamptz
)
language plpgsql
stable
security definer
set search_path = public
as $$
begin
    if not exists (
        select 1
        from public.employee_sessions s
        join public.employee_accounts e on e.id = s.employee_id
        where s.token_hash = p_token_hash
          and s.expires_at > clock_timestamp()
          and e.is_active = true
          and e.deleted_at is null
          and e.role = 'admin'
    ) then
        raise exception 'Không có quyền quản trị';
    end if;

    return query
    select e.id, e.username, e.full_name, e.role, e.is_active, e.last_login, e.created_at
    from public.employee_accounts e
    where e.deleted_at is null
    order by e.created_at desc;
end;
$$;

create or replace function public.employee_create(
    p_token_hash text,
    p_full_name text,
    p_username text,
    p_email text,
    p_password text,
    p_role text,
    p_auth_user_id uuid
)
returns table (id uuid, username text, full_name text, email text, role text, is_active boolean)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_row public.employee_accounts%rowtype;
    v_username text := lower(trim(p_username));
    v_email text := lower(trim(p_email));
begin
    if not exists (
        select 1
        from public.employee_sessions s
        join public.employee_accounts e on e.id = s.employee_id
        where s.token_hash = p_token_hash
          and s.expires_at > clock_timestamp()
          and e.is_active = true
          and e.deleted_at is null
          and e.role = 'admin'
    ) then
        raise exception 'Không có quyền quản trị';
    end if;

    if length(trim(p_full_name)) < 2 then raise exception 'Họ tên phải có ít nhất 2 ký tự'; end if;
    if v_username !~ '^[a-z0-9._-]{3,32}$' then raise exception 'Tên đăng nhập không hợp lệ'; end if;
    if v_email !~* '^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$' then raise exception 'Email không hợp lệ'; end if;
    if length(p_password) < 8 then raise exception 'Mật khẩu phải có ít nhất 8 ký tự'; end if;
    if p_role not in ('admin', 'staff') then raise exception 'Vai trò không hợp lệ'; end if;

    if exists (select 1 from public.employee_accounts e where e.username = v_username and e.deleted_at is null) then
        raise exception 'Tên đăng nhập đã tồn tại';
    end if;
    if exists (select 1 from public.employee_accounts e where lower(e.email) = v_email and e.deleted_at is null) then
        raise exception 'Email đã được sử dụng';
    end if;
    if not exists (select 1 from auth.users u where u.id = p_auth_user_id and lower(u.email) = v_email) then
        raise exception 'Tài khoản Supabase Auth không hợp lệ';
    end if;

    insert into public.employee_accounts (full_name, username, email, auth_user_id, password_hash, role)
    values (trim(p_full_name), v_username, v_email, p_auth_user_id, extensions.crypt(p_password, extensions.gen_salt('bf', 10)), p_role)
    returning * into v_row;

    return query select v_row.id, v_row.username, v_row.full_name, v_row.email, v_row.role, v_row.is_active;
exception when unique_violation then
    raise exception 'Tên đăng nhập hoặc email đã tồn tại';
end;
$$;

create or replace function public.employee_set_active(
    p_token_hash text,
    p_employee_id uuid,
    p_is_active boolean
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_admin_id uuid;
begin
    select e.id into v_admin_id
    from public.employee_sessions s
    join public.employee_accounts e on e.id = s.employee_id
    where s.token_hash = p_token_hash
      and s.expires_at > clock_timestamp()
      and e.is_active = true
      and e.deleted_at is null
      and e.role = 'admin';

    if v_admin_id is null then raise exception 'Không có quyền quản trị'; end if;
    if v_admin_id = p_employee_id and p_is_active = false then
        raise exception 'Không thể khóa chính tài khoản đang đăng nhập';
    end if;

    update public.employee_accounts set is_active = p_is_active where id = p_employee_id;
    if not p_is_active then delete from public.employee_sessions where employee_id = p_employee_id; end if;
end;
$$;

create or replace function public.employee_delete(
    p_token_hash text,
    p_employee_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_admin_id uuid;
    v_auth_user_id uuid;
begin
    select e.id into v_admin_id
    from public.employee_sessions s
    join public.employee_accounts e on e.id = s.employee_id
    where s.token_hash = p_token_hash
      and s.expires_at > clock_timestamp()
      and e.is_active = true
      and e.deleted_at is null
      and e.role = 'admin';

    if v_admin_id is null then raise exception 'Không có quyền quản trị'; end if;
    if v_admin_id = p_employee_id then raise exception 'Không thể xóa tài khoản đang đăng nhập'; end if;

    select e.auth_user_id into v_auth_user_id
    from public.employee_accounts e
    where e.id = p_employee_id and e.deleted_at is null
    for update;

    if not found then raise exception 'Không tìm thấy nhân viên hoặc tài khoản đã được xóa'; end if;

    delete from public.employee_sessions where employee_id = p_employee_id;
    delete from public.employee_locations where employee_id = p_employee_id;

    update public.employee_accounts
    set full_name = 'Nhân viên đã xóa',
        username = 'deleted_' || substring(replace(p_employee_id::text, '-', '') from 1 for 24),
        email = null,
        auth_user_id = null,
        password_hash = extensions.crypt(gen_random_uuid()::text, extensions.gen_salt('bf', 10)),
        role = 'staff',
        is_active = false,
        deleted_at = clock_timestamp()
    where id = p_employee_id;

    return jsonb_build_object('ok', true, 'auth_user_id', v_auth_user_id);
end;
$$;

create or replace function public.employee_password_reset(p_password text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_auth_user uuid := auth.uid();
    v_employee_id uuid;
begin
    if v_auth_user is null then raise exception 'Mã xác nhận không hợp lệ hoặc đã hết hạn'; end if;
    if length(coalesce(p_password, '')) < 8 or length(p_password) > 128 then
        raise exception 'Mật khẩu phải có từ 8 đến 128 ký tự';
    end if;

    update public.employee_accounts
    set password_hash = extensions.crypt(p_password, extensions.gen_salt('bf', 10)),
        updated_at = clock_timestamp()
    where auth_user_id = v_auth_user
      and is_active = true
      and deleted_at is null
    returning id into v_employee_id;

    if v_employee_id is null then
        raise exception 'Tài khoản nhân viên không tồn tại hoặc đã bị khóa';
    end if;

    delete from public.employee_sessions where employee_id = v_employee_id;
    return jsonb_build_object('ok', true);
end;
$$;

-- 12. Stored Procedures: GPS & Commands
create or replace function public.employee_location_update(
    p_token_hash text,
    p_latitude double precision,
    p_longitude double precision,
    p_accuracy double precision default null,
    p_heading double precision default null,
    p_speed double precision default null
)
returns void
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_employee_id uuid;
begin
    select e.id into v_employee_id
    from public.employee_sessions s
    join public.employee_accounts e on e.id = s.employee_id
    where s.token_hash = p_token_hash
      and s.expires_at > clock_timestamp()
      and e.is_active = true
      and e.deleted_at is null;

    if v_employee_id is null then raise exception 'Phiên đăng nhập không hợp lệ'; end if;
    if p_latitude not between -90 and 90 or p_longitude not between -180 and 180 then
        raise exception 'Tọa độ không hợp lệ';
    end if;

    insert into public.employee_locations
        (employee_id, latitude, longitude, accuracy, heading, speed, recorded_at)
    values
        (v_employee_id, p_latitude, p_longitude, p_accuracy, p_heading, p_speed, clock_timestamp())
    on conflict (employee_id) do update set
        latitude = excluded.latitude,
        longitude = excluded.longitude,
        accuracy = excluded.accuracy,
        heading = excluded.heading,
        speed = excluded.speed,
        recorded_at = excluded.recorded_at;
end;
$$;

create or replace function public.employee_location_update_if_newer(
    p_token_hash text,
    p_latitude double precision,
    p_longitude double precision,
    p_accuracy double precision default null,
    p_heading double precision default null,
    p_speed double precision default null,
    p_recorded_at timestamptz default clock_timestamp()
)
returns void
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_employee_id uuid;
    v_existing_recorded_at timestamptz;
begin
    select e.id into v_employee_id
    from public.employee_sessions s
    join public.employee_accounts e on e.id = s.employee_id
    where s.token_hash = p_token_hash
      and s.expires_at > clock_timestamp()
      and e.is_active = true
      and e.deleted_at is null;

    if v_employee_id is null then raise exception 'Phiên đăng nhập không hợp lệ'; end if;
    if p_latitude not between -90 and 90 or p_longitude not between -180 and 180 then
        raise exception 'Tọa độ không hợp lệ';
    end if;

    select recorded_at into v_existing_recorded_at
    from public.employee_locations
    where employee_id = v_employee_id;

    -- Only overwrite live location if the batch timestamp is strictly newer or no prior record
    if v_existing_recorded_at is null or p_recorded_at >= v_existing_recorded_at then
        insert into public.employee_locations
            (employee_id, latitude, longitude, accuracy, heading, speed, recorded_at)
        values
            (v_employee_id, p_latitude, p_longitude, p_accuracy, p_heading, p_speed, p_recorded_at)
        on conflict (employee_id) do update set
            latitude = excluded.latitude,
            longitude = excluded.longitude,
            accuracy = excluded.accuracy,
            heading = excluded.heading,
            speed = excluded.speed,
            recorded_at = excluded.recorded_at;
    end if;
end;
$$;

create or replace function public.employee_location_list(p_token_hash text)
returns table (
    employee_id uuid,
    username text,
    full_name text,
    role text,
    latitude double precision,
    longitude double precision,
    accuracy double precision,
    heading double precision,
    speed double precision,
    recorded_at timestamptz
)
language plpgsql
stable
security definer
set search_path = public, extensions
as $$
begin
    if not exists (
        select 1
        from public.employee_sessions s
        join public.employee_accounts e on e.id = s.employee_id
        where s.token_hash = p_token_hash
          and s.expires_at > clock_timestamp()
          and e.is_active = true
          and e.deleted_at is null
          and e.role = 'admin'
    ) then
        raise exception 'Không có quyền quản trị';
    end if;

    return query
    select e.id, e.username, e.full_name, e.role,
           l.latitude, l.longitude, l.accuracy, l.heading, l.speed, l.recorded_at
    from public.employee_accounts e
    join public.employee_locations l on l.employee_id = e.id
    where e.is_active = true and e.deleted_at is null
    order by l.recorded_at desc;
end;
$$;

create or replace function public.employee_bin_command(
    p_token_hash text,
    p_device_id text,
    p_action text
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_employee_id uuid;
    v_role text;
    v_action text := upper(trim(p_action));
    v_requested_at timestamptz := clock_timestamp();
    v_command_id uuid := gen_random_uuid();
    v_has_access boolean := false;
begin
    select e.id, e.role into v_employee_id, v_role
    from public.employee_sessions s
    join public.employee_accounts e on e.id = s.employee_id
    where s.token_hash = p_token_hash
      and s.expires_at > clock_timestamp()
      and e.is_active = true
      and e.deleted_at is null;

    if v_employee_id is null then raise exception 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn'; end if;
    if p_device_id !~ '^[A-Za-z0-9_-]{1,64}$' or v_action not in ('OPEN', 'OPEN_LID', 'CLOSE', 'CLOSE_LID', 'AUTO', 'MANUAL', 'PAUSE', 'RESUME') then
        raise exception 'Lệnh hoặc mã thiết bị không hợp lệ';
    end if;

    -- Zero-Trust Authorization: Admin has global access; Staff only has access to bins in their active IN_PROGRESS job
    if v_role = 'admin' then
        v_has_access := true;
    else
        select exists (
            select 1
            from public.collection_jobs j
            where j.employee_id = v_employee_id
              and j.status = 'IN_PROGRESS'
              and p_device_id = any(j.target_bin_ids)
        ) into v_has_access;
    end if;

    if not v_has_access then
        raise exception 'FORBIDDEN: Bạn không có quyền điều khiển thùng rác #%', p_device_id using errcode = 'P0403';
    end if;

    -- 1. Insert into dedicated device_commands queue
    insert into public.device_commands (
        id, device_id, action, status, issued_by, issued_at, expires_at
    ) values (
        v_command_id,
        p_device_id,
        v_action,
        'pending',
        v_employee_id,
        v_requested_at,
        v_requested_at + interval '30 seconds'
    );

    -- 2. Update smart_bins table for quick status reflection
    update public.smart_bins
    set last_command = v_action,
        last_command_at = v_requested_at,
        command_status = 'pending',
        command_requested_by = v_employee_id,
        command_processed_at = null
    where device_id = p_device_id;

    if not found then raise exception 'Không tìm thấy thùng rác'; end if;

    return jsonb_build_object(
        'ok', true,
        'queued', true,
        'command_id', v_command_id,
        'device_id', p_device_id,
        'action', v_action,
        'requested_at', v_requested_at
    );
end;
$$;

-- 13. Stored Procedures: Collection Workflow Transactions (Hardened & Token-Authenticated)
create or replace function public.release_bins(p_bin_ids text[], p_employee_id uuid)
returns table(released_id text)
language plpgsql
security definer
set search_path = public, extensions
as $$
begin
    return query
    update public.smart_bins
    set collection_status        = 'IDLE',
        collection_employee_id   = null,
        collection_employee_name = null,
        collection_started_at    = null
    where device_id = any(p_bin_ids)
      and (p_employee_id is null or collection_employee_id = p_employee_id)
    returning device_id;
end;
$$;

create or replace function public.rpc_assign_job(
    p_job_id        text,
    p_employee_id   uuid,
    p_employee_name text,
    p_bin_ids       text[],
    p_source        text,
    p_route_data    jsonb
) returns public.collection_jobs
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_claimed_count integer;
    v_job           public.collection_jobs;
begin
    update public.smart_bins
    set collection_status        = 'RESERVED',
        collection_employee_id   = p_employee_id,
        collection_employee_name = p_employee_name,
        collection_started_at    = clock_timestamp()
    where device_id = any(p_bin_ids)
      and (collection_status is null or collection_status = 'IDLE')
      and collection_employee_id is null;

    get diagnostics v_claimed_count = row_count;

    if v_claimed_count <> array_length(p_bin_ids, 1) then
        raise exception 'BINS_CONFLICT: Chỉ claim được %/% thùng. Một số thùng vừa được nhận bởi tài xế khác.',
            v_claimed_count, array_length(p_bin_ids, 1)
            using errcode = 'P0001';
    end if;

    insert into public.collection_jobs
        (id, employee_id, employee_name, source, status, target_bin_ids, route_data, assigned_at, version)
    values
        (p_job_id, p_employee_id, p_employee_name, p_source, 'ASSIGNED',
         p_bin_ids, p_route_data, clock_timestamp(), 1)
    returning * into v_job;

    insert into public.job_bin_items (job_id, bin_id, status)
    select p_job_id, unnest(p_bin_ids), 'PENDING';

    return v_job;
end;
$$;

create or replace function public.rpc_driver_self_pick_job(
    p_token_hash    text,
    p_job_id        text,
    p_bin_ids       text[],
    p_route_data    jsonb
) returns public.collection_jobs
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_employee_id   uuid;
    v_employee_name text;
    v_claimed_count integer;
    v_job           public.collection_jobs;
begin
    select e.id, e.full_name into v_employee_id, v_employee_name
    from public.employee_sessions s
    join public.employee_accounts e on e.id = s.employee_id
    where s.token_hash = p_token_hash
      and s.expires_at > clock_timestamp()
      and e.is_active = true
      and e.deleted_at is null;

    if v_employee_id is null then raise exception 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn'; end if;

    update public.smart_bins
    set collection_status        = 'RESERVED',
        collection_employee_id   = v_employee_id,
        collection_employee_name = v_employee_name,
        collection_started_at    = clock_timestamp()
    where device_id = any(p_bin_ids)
      and (collection_status is null or collection_status = 'IDLE')
      and collection_employee_id is null;

    get diagnostics v_claimed_count = row_count;

    if v_claimed_count <> array_length(p_bin_ids, 1) then
        raise exception 'BINS_CONFLICT: Chỉ claim được %/% thùng.',
            v_claimed_count, array_length(p_bin_ids, 1)
            using errcode = 'P0001';
    end if;

    insert into public.collection_jobs
        (id, employee_id, employee_name, source, status, target_bin_ids, route_data, started_at, version)
    values
        (p_job_id, v_employee_id, v_employee_name, 'STAFF_SELF_PICK', 'IN_PROGRESS',
         p_bin_ids, p_route_data, clock_timestamp(), 1)
    returning * into v_job;

    insert into public.job_bin_items (job_id, bin_id, status)
    select p_job_id, unnest(p_bin_ids), 'PENDING';

    return v_job;
end;
$$;

create or replace function public.rpc_driver_accept_job(
    p_token_hash text,
    p_job_id     text
) returns public.collection_jobs
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_employee_id uuid;
    v_job         public.collection_jobs;
begin
    select e.id into v_employee_id
    from public.employee_sessions s
    join public.employee_accounts e on e.id = s.employee_id
    where s.token_hash = p_token_hash
      and s.expires_at > clock_timestamp()
      and e.is_active = true
      and e.deleted_at is null;

    if v_employee_id is null then raise exception 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn'; end if;

    update public.collection_jobs
    set status = 'ACCEPTED',
        accepted_at = clock_timestamp(),
        version = version + 1
    where id = p_job_id
      and status = 'ASSIGNED'
      and employee_id = v_employee_id
    returning * into v_job;

    if not found then
        if not exists (select 1 from public.collection_jobs where id = p_job_id) then
            raise exception 'JOB_NOT_FOUND' using errcode = 'P0101';
        elsif not exists (select 1 from public.collection_jobs where id = p_job_id and employee_id = v_employee_id) then
            raise exception 'FORBIDDEN: Ca làm việc này không thuộc về bạn.' using errcode = 'P0403';
        else
            raise exception 'INVALID_STATUS: Ca làm việc không ở trạng thái ASSIGNED.' using errcode = 'P0102';
        end if;
    end if;

    return v_job;
end;
$$;

create or replace function public.rpc_driver_start_job(
    p_token_hash text,
    p_job_id     text
) returns public.collection_jobs
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_employee_id uuid;
    v_job         public.collection_jobs;
begin
    select e.id into v_employee_id
    from public.employee_sessions s
    join public.employee_accounts e on e.id = s.employee_id
    where s.token_hash = p_token_hash
      and s.expires_at > clock_timestamp()
      and e.is_active = true
      and e.deleted_at is null;

    if v_employee_id is null then raise exception 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn'; end if;

    update public.collection_jobs
    set status = 'IN_PROGRESS',
        started_at = coalesce(started_at, clock_timestamp()),
        version = version + 1
    where id = p_job_id
      and status in ('ASSIGNED', 'ACCEPTED')
      and employee_id = v_employee_id
    returning * into v_job;

    if not found then
        if not exists (select 1 from public.collection_jobs where id = p_job_id) then
            raise exception 'JOB_NOT_FOUND' using errcode = 'P0101';
        elsif not exists (select 1 from public.collection_jobs where id = p_job_id and employee_id = v_employee_id) then
            raise exception 'FORBIDDEN: Ca làm việc này không thuộc về bạn.' using errcode = 'P0403';
        else
            raise exception 'INVALID_STATUS: Ca làm việc phải ở trạng thái ASSIGNED hoặc ACCEPTED.' using errcode = 'P0102';
        end if;
    end if;

    return v_job;
end;
$$;

create or replace function public.rpc_driver_pause_job(
    p_token_hash text,
    p_job_id     text,
    p_reason     text default null
) returns public.collection_jobs
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_employee_id uuid;
    v_job         public.collection_jobs;
begin
    select e.id into v_employee_id
    from public.employee_sessions s
    join public.employee_accounts e on e.id = s.employee_id
    where s.token_hash = p_token_hash
      and s.expires_at > clock_timestamp()
      and e.is_active = true
      and e.deleted_at is null;

    if v_employee_id is null then raise exception 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn'; end if;

    update public.collection_jobs
    set status = 'PAUSED',
        paused_at = clock_timestamp(),
        pause_reason = p_reason,
        version = version + 1
    where id = p_job_id
      and status = 'IN_PROGRESS'
      and employee_id = v_employee_id
    returning * into v_job;

    if not found then
        if not exists (select 1 from public.collection_jobs where id = p_job_id) then
            raise exception 'JOB_NOT_FOUND' using errcode = 'P0101';
        elsif not exists (select 1 from public.collection_jobs where id = p_job_id and employee_id = v_employee_id) then
            raise exception 'FORBIDDEN: Ca làm việc này không thuộc về bạn.' using errcode = 'P0403';
        else
            raise exception 'INVALID_STATUS: Chỉ có thể tạm dừng ca làm việc đang IN_PROGRESS.' using errcode = 'P0102';
        end if;
    end if;

    return v_job;
end;
$$;

create or replace function public.rpc_driver_resume_job(
    p_token_hash text,
    p_job_id     text
) returns public.collection_jobs
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_employee_id uuid;
    v_job         public.collection_jobs;
begin
    select e.id into v_employee_id
    from public.employee_sessions s
    join public.employee_accounts e on e.id = s.employee_id
    where s.token_hash = p_token_hash
      and s.expires_at > clock_timestamp()
      and e.is_active = true
      and e.deleted_at is null;

    if v_employee_id is null then raise exception 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn'; end if;

    update public.collection_jobs
    set status = 'IN_PROGRESS',
        paused_at = null,
        version = version + 1
    where id = p_job_id
      and status = 'PAUSED'
      and employee_id = v_employee_id
    returning * into v_job;

    if not found then
        if not exists (select 1 from public.collection_jobs where id = p_job_id) then
            raise exception 'JOB_NOT_FOUND' using errcode = 'P0101';
        elsif not exists (select 1 from public.collection_jobs where id = p_job_id and employee_id = v_employee_id) then
            raise exception 'FORBIDDEN: Ca làm việc này không thuộc về bạn.' using errcode = 'P0403';
        else
            raise exception 'INVALID_STATUS: Chỉ có thể tiếp tục ca làm việc đang PAUSED.' using errcode = 'P0102';
        end if;
    end if;

    return v_job;
end;
$$;

create or replace function public.rpc_driver_reject_job(
    p_token_hash text,
    p_job_id     text
) returns public.collection_jobs
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_employee_id uuid;
    v_job         public.collection_jobs;
begin
    select e.id into v_employee_id
    from public.employee_sessions s
    join public.employee_accounts e on e.id = s.employee_id
    where s.token_hash = p_token_hash
      and s.expires_at > clock_timestamp()
      and e.is_active = true
      and e.deleted_at is null;

    if v_employee_id is null then raise exception 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn'; end if;

    select * into v_job from public.collection_jobs where id = p_job_id for update;
    if not found then raise exception 'JOB_NOT_FOUND' using errcode = 'P0101'; end if;
    if v_job.employee_id is distinct from v_employee_id then
        raise exception 'FORBIDDEN: Ca làm việc này không thuộc về bạn.' using errcode = 'P0403';
    end if;
    if v_job.status <> 'ASSIGNED' then
        raise exception 'INVALID_STATUS: Chỉ có thể từ chối ca làm việc đang ASSIGNED.' using errcode = 'P0102';
    end if;

    perform public.release_bins(v_job.target_bin_ids, v_job.employee_id);

    update public.collection_jobs
    set status = 'REJECTED', cancelled_at = clock_timestamp(), version = version + 1
    where id = p_job_id
    returning * into v_job;

    return v_job;
end;
$$;

create or replace function public.rpc_driver_collect_bin(
    p_token_hash text,
    p_job_id     text,
    p_bin_id     text,
    p_status     text default 'COLLECTED',
    p_note       text default null,
    p_photo_url  text default null
) returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_employee_id uuid;
    v_job         public.collection_jobs;
    v_pending_cnt integer;
begin
    select e.id into v_employee_id
    from public.employee_sessions s
    join public.employee_accounts e on e.id = s.employee_id
    where s.token_hash = p_token_hash
      and s.expires_at > clock_timestamp()
      and e.is_active = true
      and e.deleted_at is null;

    if v_employee_id is null then raise exception 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn'; end if;

    select * into v_job from public.collection_jobs where id = p_job_id for update;
    if not found then raise exception 'JOB_NOT_FOUND' using errcode = 'P0101'; end if;

    if v_job.employee_id is distinct from v_employee_id then
        raise exception 'FORBIDDEN: Bạn không có quyền thu gom cho ca làm việc của người khác.' using errcode = 'P0403';
    end if;

    if v_job.status = 'COMPLETED' then
        return jsonb_build_object('job', to_jsonb(v_job), 'all_done', true, 'idempotent', true);
    end if;

    if v_job.status not in ('IN_PROGRESS', 'PAUSED') then
        raise exception 'INVALID_STATUS: Ca làm việc phải ở trạng thái IN_PROGRESS hoặc PAUSED.' using errcode = 'P0102';
    end if;

    update public.job_bin_items
    set status       = p_status,
        collected_at = clock_timestamp(),
        note         = p_note,
        photo_url    = p_photo_url
    where job_id = p_job_id and bin_id = p_bin_id;

    if not found then
        raise exception 'BIN_NOT_IN_JOB: Thùng "%" không thuộc ca làm việc này.', p_bin_id using errcode = 'P0104';
    end if;

    update public.smart_bins
    set collection_status        = 'IDLE',
        collection_employee_id   = null,
        collection_employee_name = null,
        level_percent            = 0
    where device_id = p_bin_id
      and collection_employee_id = v_job.employee_id;

    select count(*) into v_pending_cnt
    from public.job_bin_items
    where job_id = p_job_id and status = 'PENDING';

    if v_pending_cnt = 0 then
        update public.collection_jobs
        set status = 'COMPLETED', completed_at = clock_timestamp(), version = version + 1
        where id = p_job_id
        returning * into v_job;
    end if;

    return jsonb_build_object('job', to_jsonb(v_job), 'all_done', v_pending_cnt = 0, 'idempotent', false);
end;
$$;

create or replace function public.rpc_reassign_job(
    p_old_job_id        text,
    p_old_version       integer,
    p_new_job_id        text,
    p_new_employee_id   uuid,
    p_new_employee_name text
) returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_old_job           public.collection_jobs;
    v_new_job           public.collection_jobs;
    v_remaining_bin_ids text[];
    v_transferred_count integer;
begin
    select * into v_old_job from public.collection_jobs where id = p_old_job_id for update;

    if not found then raise exception 'JOB_NOT_FOUND' using errcode = 'P0101'; end if;
    if v_old_job.status not in ('IN_PROGRESS', 'PAUSED') then
        raise exception 'INVALID_STATUS: Job đang ở trạng thái "%"', v_old_job.status using errcode = 'P0102';
    end if;
    if v_old_job.version <> p_old_version then raise exception 'VERSION_CONFLICT' using errcode = 'P0103'; end if;

    select array_agg(bin_id) into v_remaining_bin_ids
    from public.job_bin_items
    where job_id = p_old_job_id and status = 'PENDING';

    if v_remaining_bin_ids is null or array_length(v_remaining_bin_ids, 1) = 0 then
        raise exception 'NO_REMAINING_BINS: Tất cả thùng rác đã được thu gom.' using errcode = 'P0105';
    end if;

    update public.smart_bins
    set collection_employee_id   = p_new_employee_id,
        collection_employee_name = p_new_employee_name,
        collection_started_at    = clock_timestamp()
    where device_id = any(v_remaining_bin_ids)
      and collection_employee_id = v_old_job.employee_id;

    get diagnostics v_transferred_count = row_count;

    if v_transferred_count <> array_length(v_remaining_bin_ids, 1) then
        raise exception 'TRANSFER_CONFLICT: Chỉ chuyển được %/% thùng.',
            v_transferred_count, array_length(v_remaining_bin_ids, 1) using errcode = 'P0106';
    end if;

    update public.collection_jobs
    set status = 'CANCELLED', cancelled_at = clock_timestamp(), version = version + 1
    where id = p_old_job_id
    returning * into v_old_job;

    insert into public.collection_jobs
        (id, employee_id, employee_name, source, status, target_bin_ids, route_data, reassigned_from_job, assigned_at, version)
    values
        (p_new_job_id, p_new_employee_id, p_new_employee_name, 'ADMIN_ASSIGNED', 'ASSIGNED',
         v_remaining_bin_ids, null, p_old_job_id, clock_timestamp(), 1)
    returning * into v_new_job;

    insert into public.job_bin_items (job_id, bin_id, status)
    select p_new_job_id, unnest(v_remaining_bin_ids), 'PENDING';

    return jsonb_build_object(
        'old_job', to_jsonb(v_old_job),
        'new_job', to_jsonb(v_new_job),
        'remaining_bin_ids', to_jsonb(v_remaining_bin_ids)
    );
end;
$$;

create or replace function public.rpc_cancel_job(p_job_id text, p_expected_version integer)
returns public.collection_jobs
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_job public.collection_jobs;
begin
    select * into v_job from public.collection_jobs where id = p_job_id for update;
    if not found then raise exception 'JOB_NOT_FOUND' using errcode = 'P0101'; end if;
    if v_job.status not in ('PENDING', 'ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'PAUSED') then
        raise exception 'INVALID_STATUS: Job đang ở trạng thái "%"', v_job.status using errcode = 'P0102';
    end if;
    if v_job.version <> p_expected_version then raise exception 'VERSION_CONFLICT' using errcode = 'P0103'; end if;

    if v_job.employee_id is not null then
        perform public.release_bins(v_job.target_bin_ids, v_job.employee_id);
    end if;

    update public.collection_jobs
    set status = 'CANCELLED', cancelled_at = clock_timestamp(), version = version + 1
    where id = p_job_id
    returning * into v_job;

    return v_job;
end;
$$;

create or replace function public.rpc_expire_job(p_job_id text)
returns public.collection_jobs
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_job public.collection_jobs;
begin
    select * into v_job from public.collection_jobs where id = p_job_id for update;
    if not found or v_job.status <> 'ASSIGNED' then return null; end if;

    perform public.release_bins(v_job.target_bin_ids, v_job.employee_id);

    update public.collection_jobs
    set status = 'EXPIRED', cancelled_at = clock_timestamp(), version = version + 1
    where id = p_job_id
    returning * into v_job;

    return v_job;
end;
$$;

create or replace function public.employee_collection_update(
    p_token_hash text,
    p_device_id text,
    p_action text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_employee_id uuid;
    v_employee_name text;
    v_role text;
    v_action text := upper(trim(p_action));
    v_now timestamptz := clock_timestamp();
    v_bin public.smart_bins%rowtype;
begin
    select e.id, e.full_name, e.role into v_employee_id, v_employee_name, v_role
    from public.employee_sessions s
    join public.employee_accounts e on e.id = s.employee_id
    where s.token_hash = p_token_hash
      and s.expires_at > clock_timestamp()
      and e.is_active = true
      and e.deleted_at is null;

    if v_employee_id is null then raise exception 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn'; end if;
    if p_device_id !~ '^[A-Za-z0-9_-]{1,64}$' or v_action not in ('START', 'COMPLETE') then
        raise exception 'Thao tác hoặc mã thiết bị không hợp lệ';
    end if;

    select * into v_bin from public.smart_bins where device_id = p_device_id for update;
    if not found then raise exception 'Không tìm thấy thùng rác'; end if;

    if v_action = 'START' then
        if v_bin.collection_status = 'IN_PROGRESS' then
            raise exception 'Thùng rác đang được % thu gom', coalesce(v_bin.collection_employee_name, 'nhân viên khác');
        end if;

        insert into public.bin_collections (device_id, employee_id, employee_name, status, started_at)
        values (p_device_id, v_employee_id, v_employee_name, 'IN_PROGRESS', v_now);

        update public.smart_bins
        set collection_status = 'IN_PROGRESS',
            collection_employee_id = v_employee_id,
            collection_employee_name = v_employee_name,
            collection_started_at = v_now,
            collection_completed_at = null,
            collection_paused = true,
            last_command = 'PAUSE',
            last_command_at = v_now,
            command_status = 'pending',
            command_requested_by = v_employee_id,
            command_processed_at = null
        where device_id = p_device_id;
    else
        if v_bin.collection_status <> 'IN_PROGRESS' then
            raise exception 'Thùng rác chưa ở trạng thái đang thu gom';
        end if;
        if v_bin.collection_employee_id is distinct from v_employee_id and v_role <> 'admin' then
            raise exception 'Chỉ nhân viên bắt đầu thu gom hoặc quản trị viên mới được hoàn tất';
        end if;

        update public.bin_collections
        set status = 'COMPLETED', completed_at = v_now
        where device_id = p_device_id and status = 'IN_PROGRESS';

        update public.smart_bins
        set collection_status = 'IDLE',
            collection_completed_at = v_now,
            collection_paused = false,
            level_percent = 0,
            last_command = 'RESUME',
            last_command_at = v_now,
            command_status = 'pending',
            command_requested_by = v_employee_id,
            command_processed_at = null
        where device_id = p_device_id;
    end if;

    return jsonb_build_object(
        'ok', true,
        'device_id', p_device_id,
        'action', v_action,
        'collection_status', case when v_action = 'START' then 'IN_PROGRESS' else 'IDLE' end,
        'employee_id', v_employee_id,
        'employee_name', v_employee_name,
        'changed_at', v_now
    );
end;
$$;

create or replace function public.employee_daily_collection_count(p_token_hash text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_employee_id uuid;
    v_day date := (clock_timestamp() at time zone 'Asia/Ho_Chi_Minh')::date;
    v_start timestamptz;
    v_end timestamptz;
    v_count integer;
begin
    select e.id into v_employee_id
    from public.employee_sessions s
    join public.employee_accounts e on e.id = s.employee_id
    where s.token_hash = p_token_hash
      and s.expires_at > clock_timestamp()
      and e.is_active = true
      and e.deleted_at is null;

    if v_employee_id is null then raise exception 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn'; end if;

    v_start := v_day::timestamp at time zone 'Asia/Ho_Chi_Minh';
    v_end := (v_day + 1)::timestamp at time zone 'Asia/Ho_Chi_Minh';

    select count(*)::integer into v_count
    from public.bin_collections
    where status = 'COMPLETED'
      and completed_at >= v_start
      and completed_at < v_end;

    return jsonb_build_object('count', v_count, 'day', v_day, 'timezone', 'Asia/Ho_Chi_Minh');
end;
$$;

-- 14. Stored Procedures: Incident Image Upload & Verification
create or replace function public.employee_incident_upload_prepare(
    p_token_hash text,
    p_device_id text,
    p_reason text,
    p_description text default ''
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_employee_id uuid;
    v_upload_id uuid := gen_random_uuid();
    v_object_path text;
begin
    select e.id into v_employee_id
    from public.employee_sessions s
    join public.employee_accounts e on e.id = s.employee_id
    where s.token_hash = p_token_hash
      and s.expires_at > clock_timestamp()
      and e.is_active = true
      and e.deleted_at is null
    limit 1;

    if v_employee_id is null then raise exception 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn'; end if;
    if p_device_id !~ '^[A-Za-z0-9_-]{1,64}$' then raise exception 'Mã thùng rác không hợp lệ'; end if;
    if length(trim(coalesce(p_reason, ''))) < 3 or length(trim(coalesce(p_reason, ''))) > 120 then
        raise exception 'Vui lòng chọn lý do sự cố hợp lệ';
    end if;
    if length(coalesce(p_description, '')) > 500 then raise exception 'Mô tả tối đa 500 ký tự'; end if;

    v_object_path := 'incidents/' || p_device_id || '/'
        || to_char(clock_timestamp(), 'YYYYMMDD_HH24MISS') || '_' || substr(md5(random()::text), 1, 4) || '.jpg';

    insert into public.incident_image_uploads (
        id, employee_id, device_id, reason, description, object_path, expires_at
    ) values (
        v_upload_id,
        v_employee_id,
        p_device_id,
        trim(p_reason),
        trim(coalesce(p_description, '')),
        v_object_path,
        clock_timestamp() + interval '10 minutes'
    );

    return jsonb_build_object(
        'upload_id', v_upload_id,
        'object_path', v_object_path,
        'expires_at', clock_timestamp() + interval '10 minutes'
    );
end;
$$;

create or replace function public.incident_upload_path_allowed(p_object_path text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.incident_image_uploads u
        where u.object_path = p_object_path
          and u.completed_at is null
          and u.expires_at > clock_timestamp()
    );
$$;

drop policy if exists "incident_capability_upload" on storage.objects;
create policy "incident_capability_upload"
on storage.objects
for insert
to anon, authenticated
with check (
    bucket_id = 'incident-images'
    and name ~ '^incidents/[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+\.jpg$'
    and public.incident_upload_path_allowed(name)
);

create or replace function public.employee_incident_upload_finalize(
    p_token_hash text,
    p_upload_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_employee_id uuid;
    v_upload public.incident_image_uploads%rowtype;
    v_report_id bigint;
    v_size bigint;
    v_mime text;
begin
    select e.id into v_employee_id
    from public.employee_sessions s
    join public.employee_accounts e on e.id = s.employee_id
    where s.token_hash = p_token_hash
      and s.expires_at > clock_timestamp()
      and e.is_active = true
      and e.deleted_at is null
    limit 1;

    if v_employee_id is null then raise exception 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn'; end if;

    select u.* into v_upload
    from public.incident_image_uploads u
    where u.id = p_upload_id
      and u.employee_id = v_employee_id
    for update;

    if not found then raise exception 'Phiên tải ảnh không hợp lệ'; end if;

    if v_upload.completed_at is not null and v_upload.report_id is not null then
        return jsonb_build_object(
            'ok', true,
            'report_id', v_upload.report_id,
            'proof_image_path', v_upload.object_path
        );
    end if;

    if v_upload.expires_at <= clock_timestamp() then
        raise exception 'Phiên tải ảnh đã hết hạn. Vui lòng gửi lại báo cáo';
    end if;

    select
        coalesce(nullif(o.metadata ->> 'size', '')::bigint, 0),
        lower(coalesce(o.metadata ->> 'mimetype', ''))
    into v_size, v_mime
    from storage.objects o
    where o.bucket_id = 'incident-images'
      and o.name = v_upload.object_path
    limit 1;

    if not found then raise exception 'Chưa nhận được ảnh minh chứng'; end if;
    if v_size <= 0 or v_size > 5242880 then raise exception 'Ảnh minh chứng phải nhỏ hơn 5 MB'; end if;
    if v_mime not in ('image/jpeg', 'image/jpg') then raise exception 'Ảnh minh chứng phải có định dạng JPEG'; end if;

    insert into public.incident_reports (
        device_id, employee_id, employee_name, reason, description, has_photo, proof_image_url
    )
    select
        v_upload.device_id, e.id, e.full_name, v_upload.reason, v_upload.description, true, v_upload.object_path
    from public.employee_accounts e
    where e.id = v_employee_id
    returning id into v_report_id;

    update public.incident_image_uploads
    set report_id = v_report_id, completed_at = clock_timestamp()
    where id = v_upload.id;

    return jsonb_build_object(
        'ok', true,
        'report_id', v_report_id,
        'proof_image_path', v_upload.object_path
    );
end;
$$;

create or replace function public.employee_incident_report(
    p_token_hash text,
    p_device_id text,
    p_reason text,
    p_description text default '',
    p_has_photo boolean default false
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_employee_id uuid;
    v_employee_name text;
    v_report_id bigint;
begin
    select e.id, e.full_name into v_employee_id, v_employee_name
    from public.employee_sessions s
    join public.employee_accounts e on e.id = s.employee_id
    where s.token_hash = p_token_hash
      and s.expires_at > clock_timestamp()
      and e.is_active = true
      and e.deleted_at is null
    limit 1;

    if v_employee_id is null then raise exception 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn'; end if;
    if p_device_id !~ '^[A-Za-z0-9_-]{1,64}$' then raise exception 'Mã thùng rác không hợp lệ'; end if;
    if length(trim(coalesce(p_reason, ''))) < 3 then raise exception 'Vui lòng chọn lý do sự cố'; end if;
    if length(coalesce(p_description, '')) > 500 then raise exception 'Mô tả tối đa 500 ký tự'; end if;

    insert into public.incident_reports (
        device_id, employee_id, employee_name, reason, description, has_photo, proof_image_url
    ) values (
        p_device_id, v_employee_id, v_employee_name, trim(p_reason), trim(coalesce(p_description, '')), false, null
    ) returning id into v_report_id;

    return jsonb_build_object('ok', true, 'report_id', v_report_id, 'status', 'NEW');
end;
$$;

-- 15. Row Level Security & Grants (Zero-Trust Hardened)
alter table public.smart_bins enable row level security;
alter table public.bin_events enable row level security;
alter table public.device_commands enable row level security;
alter table public.employee_accounts enable row level security;
alter table public.employee_sessions enable row level security;
alter table public.employee_locations enable row level security;
alter table public.employee_location_points enable row level security;
alter table public.collection_jobs enable row level security;
alter table public.job_bin_items enable row level security;
alter table public.bin_collections enable row level security;
alter table public.incident_reports enable row level security;
alter table public.incident_image_uploads enable row level security;

-- Read-only policy for public smart_bins and bin_events telemetry
drop policy if exists "anon_read_smart_bins" on public.smart_bins;
drop policy if exists "anon_insert_smart_bins" on public.smart_bins;
drop policy if exists "anon_update_smart_bins" on public.smart_bins;
drop policy if exists "anon_read_bin_events" on public.bin_events;
drop policy if exists "anon_insert_bin_events" on public.bin_events;

create policy "anon_read_smart_bins" on public.smart_bins for select to anon, authenticated using (true);
create policy "anon_read_bin_events" on public.bin_events for select to anon, authenticated using (true);

-- Revoke direct table manipulation from anon / authenticated clients (Mutations MUST go through trusted RPC or backend service_role)
revoke all on public.smart_bins from anon, authenticated;
revoke all on public.bin_events from anon, authenticated;
revoke all on public.device_commands from anon, authenticated;
revoke all on public.employee_accounts from anon, authenticated;
revoke all on public.employee_sessions from anon, authenticated;
revoke all on public.employee_locations from anon, authenticated;
revoke all on public.employee_location_points from anon, authenticated;
revoke all on public.collection_jobs from anon, authenticated;
revoke all on public.job_bin_items from anon, authenticated;
revoke all on public.bin_collections from anon, authenticated;
revoke all on public.incident_reports from anon, authenticated;
revoke all on public.incident_image_uploads from anon, authenticated;

grant select on public.smart_bins to anon, authenticated;
grant select on public.bin_events to anon, authenticated;
grant usage, select on all sequences in schema public to anon, authenticated;

-- Public/Authenticated Accessible Token-Validated RPCs
grant execute on function public.employee_login(text, text, text) to anon, authenticated;
grant execute on function public.employee_current(text) to anon, authenticated;
grant execute on function public.employee_logout(text) to anon, authenticated;
grant execute on function public.employee_list(text) to anon, authenticated;
grant execute on function public.employee_create(text, text, text, text, text, text, uuid) to anon, authenticated;
grant execute on function public.employee_set_active(text, uuid, boolean) to anon, authenticated;
grant execute on function public.employee_delete(text, uuid) to anon, authenticated;
grant execute on function public.employee_password_reset(text) to authenticated;
grant execute on function public.employee_location_update(text, double precision, double precision, double precision, double precision, double precision) to anon, authenticated;
grant execute on function public.employee_location_update_if_newer(text, double precision, double precision, double precision, double precision, double precision, timestamptz) to anon, authenticated;
grant execute on function public.employee_location_list(text) to anon, authenticated;
grant execute on function public.employee_bin_command(text, text, text) to anon, authenticated;
grant execute on function public.employee_collection_update(text, text, text) to anon, authenticated;
grant execute on function public.employee_daily_collection_count(text) to anon, authenticated;
grant execute on function public.incident_upload_path_allowed(text) to anon, authenticated;
grant execute on function public.employee_incident_upload_prepare(text, text, text, text) to anon, authenticated;
grant execute on function public.employee_incident_upload_finalize(text, uuid) to anon, authenticated;
grant execute on function public.employee_incident_report(text, text, text, text, boolean) to anon, authenticated;

-- Driver Token-Authenticated Job Transitions
grant execute on function public.rpc_driver_self_pick_job(text, text, text[], jsonb) to anon, authenticated;
grant execute on function public.rpc_driver_accept_job(text, text) to anon, authenticated;
grant execute on function public.rpc_driver_start_job(text, text) to anon, authenticated;
grant execute on function public.rpc_driver_pause_job(text, text, text) to anon, authenticated;
grant execute on function public.rpc_driver_resume_job(text, text) to anon, authenticated;
grant execute on function public.rpc_driver_reject_job(text, text) to anon, authenticated;
grant execute on function public.rpc_driver_collect_bin(text, text, text, text, text, text) to anon, authenticated;

-- Service Role (Backend Server) Full Master Grants
grant select, insert, update, delete on all tables in schema public to service_role;
grant execute on all functions in schema public to service_role;

grant execute on function public.release_bins(text[], uuid) to service_role;
grant execute on function public.rpc_assign_job(text, uuid, text, text[], text, jsonb) to service_role;
grant execute on function public.rpc_reassign_job(text, integer, text, uuid, text) to service_role;
grant execute on function public.rpc_cancel_job(text, integer) to service_role;
grant execute on function public.rpc_expire_job(text) to service_role;

-- =========================================================
-- 16. FIRMWARE RELEASES & ENTERPRISE OTA MANAGEMENT
-- =========================================================

-- Bổ sung trường firmware tracking vào smart_bins nếu chưa có
alter table public.smart_bins add column if not exists firmware_version text default 'v1.0.0';
alter table public.smart_bins add column if not exists device_model text default 'ESP32-S3-SMARTBIN';
alter table public.smart_bins add column if not exists ota_status text default 'IDLE';
alter table public.smart_bins add column if not exists last_ota_at timestamptz;
alter table public.smart_bins add column if not exists last_boot_id text;

-- Khởi tạo Supabase Storage Private Bucket cho Firmware Binary
insert into storage.buckets (id, name, public)
values ('firmware-releases', 'firmware-releases', false)
on conflict (id) do nothing;

-- 16.1. Bảng lưu trữ bản phát hành firmware
create table if not exists public.firmware_releases (
    id uuid primary key default gen_random_uuid(),
    version text not null,
    device_model text not null default 'ESP32-S3-SMARTBIN',
    file_name text not null,
    object_path text not null,
    size_bytes bigint not null check (size_bytes > 0),
    sha256 text not null unique,
    signature text,
    release_notes text,
    status text not null default 'READY' check (status in ('DRAFT', 'VALIDATING', 'READY', 'REVOKED')),
    created_by uuid references public.employee_accounts(id),
    created_at timestamptz default clock_timestamp(),
    published_at timestamptz default clock_timestamp(),
    constraint uq_firmware_model_version unique (device_model, version)
);

create index if not exists idx_firmware_releases_model_ver on public.firmware_releases (device_model, version);
create index if not exists idx_firmware_releases_sha256 on public.firmware_releases (sha256);

-- 16.2. Bảng quản lý chiến dịch cập nhật OTA
create table if not exists public.ota_deployments (
    id uuid primary key default gen_random_uuid(),
    release_id uuid not null references public.firmware_releases(id) on delete cascade,
    status text not null default 'RUNNING' check (status in ('CREATED', 'RUNNING', 'COMPLETED', 'PARTIAL_FAILED', 'CANCELLED')),
    target_count integer not null default 0,
    success_count integer not null default 0,
    failed_count integer not null default 0,
    created_by uuid references public.employee_accounts(id),
    created_at timestamptz default clock_timestamp(),
    started_at timestamptz default clock_timestamp(),
    completed_at timestamptz
);

create index if not exists idx_ota_deployments_created on public.ota_deployments (created_at desc);

-- 16.3. Bảng quản lý tiến trình OTA chi tiết của từng thiết bị
create table if not exists public.ota_device_jobs (
    id uuid primary key default gen_random_uuid(),
    deployment_id uuid not null references public.ota_deployments(id) on delete cascade,
    release_id uuid not null references public.firmware_releases(id) on delete cascade,
    device_id text not null,
    previous_version text,
    target_version text not null,
    status text not null default 'PENDING' check (status in ('PENDING', 'COMMAND_SENT', 'DOWNLOADING', 'VERIFYING', 'INSTALLING', 'REBOOTING', 'BOOT_VERIFYING', 'SUCCESS', 'FAILED', 'ROLLBACK_STARTED', 'ROLLBACK_SUCCESS', 'ROLLBACK_FAILED', 'TIMED_OUT')),
    progress_percent integer default 0 check (progress_percent >= 0 and progress_percent <= 100),
    downloaded_bytes bigint default 0,
    total_bytes bigint default 0,
    attempts integer default 1,
    error_code text,
    error_message text,
    command_id uuid not null,
    boot_id_before text,
    boot_id_after text,
    created_at timestamptz default clock_timestamp(),
    started_at timestamptz,
    updated_at timestamptz default clock_timestamp(),
    acknowledged_at timestamptz,
    constraint uq_deployment_device unique (deployment_id, device_id)
);

create index if not exists idx_ota_device_jobs_dev_status on public.ota_device_jobs (device_id, status);
create index if not exists idx_ota_device_jobs_dep on public.ota_device_jobs (deployment_id);

-- 16.4. RLS Policies & Master Grants cho OTA
alter table public.firmware_releases enable row level security;
alter table public.ota_deployments enable row level security;
alter table public.ota_device_jobs enable row level security;

revoke all on public.firmware_releases from anon, authenticated;
revoke all on public.ota_deployments from anon, authenticated;
revoke all on public.ota_device_jobs from anon, authenticated;

grant select on public.firmware_releases to authenticated;
grant select on public.ota_deployments to authenticated;
grant select on public.ota_device_jobs to authenticated;

grant select, insert, update, delete on public.firmware_releases to service_role;
grant select, insert, update, delete on public.ota_deployments to service_role;
grant select, insert, update, delete on public.ota_device_jobs to service_role;

-- =========================================================
-- 17. SUPABASE REALTIME PUBLICATION & CACHE RELOAD
-- =========================================================
do $$
begin
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'smart_bins') then
        alter publication supabase_realtime add table public.smart_bins;
    end if;
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'device_commands') then
        alter publication supabase_realtime add table public.device_commands;
    end if;
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'collection_jobs') then
        alter publication supabase_realtime add table public.collection_jobs;
    end if;
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'job_bin_items') then
        alter publication supabase_realtime add table public.job_bin_items;
    end if;
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'incident_reports') then
        alter publication supabase_realtime add table public.incident_reports;
    end if;
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'ota_deployments') then
        alter publication supabase_realtime add table public.ota_deployments;
    end if;
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'ota_device_jobs') then
        alter publication supabase_realtime add table public.ota_device_jobs;
    end if;
end $$;

notify pgrst, 'reload schema';



