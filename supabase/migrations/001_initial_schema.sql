-- Tiffin Mess — Initial PostgreSQL Schema for Supabase
-- Run this migration first in Supabase SQL Editor or via `supabase db push`.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. User profiles (1:1 with auth.users)
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL DEFAULT '',
    email TEXT NOT NULL DEFAULT '',
    avatar_url TEXT,
    phone TEXT NOT NULL DEFAULT '',
    role TEXT NOT NULL DEFAULT 'customer' CHECK (role IN ('customer', 'admin', 'owner')),
    account_status TEXT NOT NULL DEFAULT 'pending' CHECK (account_status IN ('pending', 'active', 'suspended', 'rejected')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. Messes (multi-tenant isolation boundary)
CREATE TABLE IF NOT EXISTS public.messes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    owner_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    phone TEXT NOT NULL DEFAULT '',
    address TEXT NOT NULL DEFAULT '',
    timezone TEXT NOT NULL DEFAULT 'Asia/Kolkata',
    currency TEXT NOT NULL DEFAULT 'INR',
    meal_slots JSONB NOT NULL DEFAULT '["lunch", "dinner"]'::jsonb,
    registration_code_hash TEXT NOT NULL,
    default_due_day INTEGER NOT NULL DEFAULT 5 CHECK (default_due_day BETWEEN 1 AND 28),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3. Mess members (user <-> mess membership & role)
CREATE TABLE IF NOT EXISTS public.mess_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mess_id UUID NOT NULL REFERENCES public.messes(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    member_role TEXT NOT NULL CHECK (member_role IN ('customer', 'admin', 'owner')),
    membership_status TEXT NOT NULL DEFAULT 'pending' CHECK (membership_status IN ('pending', 'active', 'suspended', 'rejected')),
    joined_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (mess_id, user_id)
);

-- 4. Customer profiles (supports both invited & Google-authenticated customers)
CREATE TABLE IF NOT EXISTS public.customer_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mess_id UUID NOT NULL REFERENCES public.messes(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    full_name TEXT NOT NULL,
    phone TEXT NOT NULL,
    email TEXT,
    address_or_room TEXT NOT NULL DEFAULT '',
    customer_status TEXT NOT NULL DEFAULT 'active' CHECK (customer_status IN ('pending', 'active', 'suspended', 'rejected')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (mess_id, user_id)
);

-- 5. Customer registration requests via mess code
CREATE TABLE IF NOT EXISTS public.registration_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mess_id UUID NOT NULL REFERENCES public.messes(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    requested_name TEXT NOT NULL,
    phone TEXT NOT NULL,
    address_or_room TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    reviewed_by UUID REFERENCES public.profiles(id),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (mess_id, user_id)
);

-- 6. Subscription plans
CREATE TABLE IF NOT EXISTS public.subscription_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mess_id UUID NOT NULL REFERENCES public.messes(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    duration_days INTEGER NOT NULL CHECK (duration_days > 0),
    price NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    included_tiffins INTEGER NOT NULL CHECK (included_tiffins >= 0),
    meal_slots JSONB NOT NULL DEFAULT '["lunch", "dinner"]'::jsonb,
    extra_tiffin_price NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (extra_tiffin_price >= 0),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 7. Customer subscriptions (with plan snapshot for historical accuracy)
CREATE TABLE IF NOT EXISTS public.subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mess_id UUID NOT NULL REFERENCES public.messes(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES public.customer_profiles(id) ON DELETE CASCADE,
    plan_id UUID REFERENCES public.subscription_plans(id) ON DELETE SET NULL,
    plan_name_snapshot TEXT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    included_tiffins INTEGER NOT NULL CHECK (included_tiffins >= 0),
    extra_tiffin_price_snapshot NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (extra_tiffin_price_snapshot >= 0),
    meal_slots_snapshot JSONB NOT NULL DEFAULT '["lunch", "dinner"]'::jsonb,
    agreed_price NUMERIC(12, 2) NOT NULL CHECK (agreed_price >= 0),
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'expiring_soon', 'expired', 'paused', 'cancelled')),
    created_by UUID REFERENCES public.profiles(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (end_date >= start_date)
);

-- 8. Daily tiffin delivery records
CREATE TABLE IF NOT EXISTS public.tiffin_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mess_id UUID NOT NULL REFERENCES public.messes(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES public.customer_profiles(id) ON DELETE CASCADE,
    subscription_id UUID REFERENCES public.subscriptions(id) ON DELETE SET NULL,
    service_date DATE NOT NULL,
    meal_slot TEXT NOT NULL CHECK (meal_slot IN ('breakfast', 'lunch', 'dinner', 'daily')),
    status TEXT NOT NULL CHECK (status IN ('delivered', 'not_delivered', 'cancelled')),
    delivered_at TIMESTAMPTZ,
    marked_by UUID REFERENCES public.profiles(id),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (mess_id, customer_id, service_date, meal_slot)
);

-- 9. Daily menus (draft vs published)
CREATE TABLE IF NOT EXISTS public.daily_menus (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mess_id UUID NOT NULL REFERENCES public.messes(id) ON DELETE CASCADE,
    menu_date DATE NOT NULL,
    breakfast TEXT,
    lunch TEXT,
    dinner TEXT,
    special_items TEXT,
    notes TEXT,
    status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published')),
    published_at TIMESTAMPTZ,
    created_by UUID REFERENCES public.profiles(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (mess_id, menu_date)
);

-- 10. Monthly bills (billing_month normalized to 1st day of month)
CREATE TABLE IF NOT EXISTS public.monthly_bills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mess_id UUID NOT NULL REFERENCES public.messes(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES public.customer_profiles(id) ON DELETE CASCADE,
    subscription_id UUID REFERENCES public.subscriptions(id) ON DELETE SET NULL,
    billing_month DATE NOT NULL CHECK (date_trunc('month', billing_month)::date = billing_month),
    subscription_charge NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (subscription_charge >= 0),
    extra_tiffin_count INTEGER NOT NULL DEFAULT 0 CHECK (extra_tiffin_count >= 0),
    extra_tiffin_charge NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (extra_tiffin_charge >= 0),
    discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    previous_balance NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (previous_balance >= 0),
    total_amount NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    amount_paid NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (amount_paid >= 0),
    balance_due NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (balance_due >= 0),
    due_date DATE NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('paid', 'partially_paid', 'pending', 'overdue')),
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (mess_id, customer_id, billing_month)
);

-- 11. Payments (immutable financial ledger; reversals preserve history)
CREATE TABLE IF NOT EXISTS public.payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mess_id UUID NOT NULL REFERENCES public.messes(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES public.customer_profiles(id) ON DELETE CASCADE,
    bill_id UUID NOT NULL REFERENCES public.monthly_bills(id) ON DELETE RESTRICT,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    payment_method TEXT NOT NULL CHECK (payment_method IN ('cash', 'upi', 'bank_transfer', 'other')),
    payment_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    reference_note TEXT,
    recorded_by UUID NOT NULL REFERENCES public.profiles(id),
    status TEXT NOT NULL DEFAULT 'confirmed' CHECK (status IN ('confirmed', 'reversed')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 12. Audit logs
CREATE TABLE IF NOT EXISTS public.audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mess_id UUID NOT NULL REFERENCES public.messes(id) ON DELETE CASCADE,
    actor_id UUID REFERENCES public.profiles(id),
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id UUID,
    old_values JSONB,
    new_values JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 13. Supporting: Subscription renewal requests from customers
CREATE TABLE IF NOT EXISTS public.subscription_renewal_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mess_id UUID NOT NULL REFERENCES public.messes(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES public.customer_profiles(id) ON DELETE CASCADE,
    requested_plan_id UUID REFERENCES public.subscription_plans(id) ON DELETE SET NULL,
    note TEXT,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    reviewed_by UUID REFERENCES public.profiles(id),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 14. Supporting: Owner-managed admin invitations
CREATE TABLE IF NOT EXISTS public.admin_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mess_id UUID NOT NULL REFERENCES public.messes(id) ON DELETE CASCADE,
    invited_email TEXT NOT NULL,
    invited_role TEXT NOT NULL DEFAULT 'admin' CHECK (invited_role IN ('admin')),
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'accepted', 'revoked')),
    invited_by UUID NOT NULL REFERENCES public.profiles(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (mess_id, invited_email)
);

-- Performance Indexes
CREATE INDEX IF NOT EXISTS idx_mess_members_user ON public.mess_members(user_id, membership_status);
CREATE INDEX IF NOT EXISTS idx_mess_members_mess ON public.mess_members(mess_id, member_role, membership_status);
CREATE INDEX IF NOT EXISTS idx_customer_profiles_mess ON public.customer_profiles(mess_id, customer_status);
CREATE INDEX IF NOT EXISTS idx_customer_profiles_email ON public.customer_profiles(lower(email));
CREATE INDEX IF NOT EXISTS idx_registration_requests_mess_status ON public.registration_requests(mess_id, status);
CREATE INDEX IF NOT EXISTS idx_subscriptions_customer ON public.subscriptions(customer_id, status);
CREATE INDEX IF NOT EXISTS idx_subscriptions_mess ON public.subscriptions(mess_id, status);
CREATE INDEX IF NOT EXISTS idx_tiffin_records_mess_date ON public.tiffin_records(mess_id, service_date);
CREATE INDEX IF NOT EXISTS idx_tiffin_records_customer_date ON public.tiffin_records(customer_id, service_date DESC);
CREATE INDEX IF NOT EXISTS idx_daily_menus_mess_date ON public.daily_menus(mess_id, menu_date DESC);
CREATE INDEX IF NOT EXISTS idx_monthly_bills_mess_month ON public.monthly_bills(mess_id, billing_month DESC);
CREATE INDEX IF NOT EXISTS idx_monthly_bills_customer_month ON public.monthly_bills(customer_id, billing_month DESC);
CREATE INDEX IF NOT EXISTS idx_payments_bill ON public.payments(bill_id, status);
CREATE INDEX IF NOT EXISTS idx_payments_mess_date ON public.payments(mess_id, payment_date DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_mess_created ON public.audit_logs(mess_id, created_at DESC);

-- Generic updated_at trigger function
CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'profiles', 'messes', 'customer_profiles', 'subscription_plans',
        'subscriptions', 'tiffin_records', 'daily_menus', 'monthly_bills', 'payments'
    ]
    LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS trg_%I_updated_at ON public.%I;', tbl, tbl);
        EXECUTE format('CREATE TRIGGER trg_%I_updated_at BEFORE UPDATE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();', tbl, tbl);
    END LOOP;
END;
$$;

-- Automatically create a profile row when a new Google/Supabase auth user signs up
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    INSERT INTO public.profiles (id, full_name, email, avatar_url, role, account_status)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data ->> 'full_name', NEW.raw_user_meta_data ->> 'name', ''),
        COALESCE(NEW.email, ''),
        COALESCE(NEW.raw_user_meta_data ->> 'avatar_url', NEW.raw_user_meta_data ->> 'picture'),
        'customer',
        'pending'
    )
    ON CONFLICT (id) DO UPDATE SET
        email = EXCLUDED.email,
        avatar_url = COALESCE(EXCLUDED.avatar_url, public.profiles.avatar_url),
        full_name = CASE WHEN public.profiles.full_name = '' THEN EXCLUDED.full_name ELSE public.profiles.full_name END;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();
