create table if not exists public.jarvis_profiles (
    google_sub text primary key,
    email text not null,
    display_name text not null default '',
    avatar_url text not null default '',
    created_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now()
);

create table if not exists public.jarvis_global_owner (
    singleton boolean primary key default true check (singleton),
    google_sub text not null references public.jarvis_profiles(google_sub) on delete restrict,
    claimed_at timestamptz not null default now()
);

alter table public.jarvis_profiles enable row level security;
alter table public.jarvis_global_owner enable row level security;

revoke all on table public.jarvis_profiles from anon, authenticated;
revoke all on table public.jarvis_global_owner from anon, authenticated;

create or replace function public.claim_jarvis_owner(p_google_sub text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    current_owner text;
begin
    if p_google_sub is null or length(trim(p_google_sub)) < 5 then
        raise exception 'Invalid Google subject';
    end if;

    insert into public.jarvis_global_owner (singleton, google_sub)
    values (true, p_google_sub)
    on conflict (singleton) do nothing;

    select google_sub into current_owner
    from public.jarvis_global_owner
    where singleton = true;

    return current_owner = p_google_sub;
end;
$$;

revoke all on function public.claim_jarvis_owner(text) from public, anon, authenticated;
grant execute on function public.claim_jarvis_owner(text) to service_role;
