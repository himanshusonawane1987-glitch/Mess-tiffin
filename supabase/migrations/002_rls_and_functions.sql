-- Tiffin Mess — Row Level Security Policies, Privilege Guards, and Trusted RPCs
-- Run after 001_initial_schema.sql.

-- Enable RLS on all tables
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mess_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.customer_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.registration_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.subscription_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tiffin_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.daily_menus ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.monthly_bills ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.subscription_renewal_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_invitations ENABLE ROW LEVEL SECURITY;

-- Protect registration_code_hash from ever being queried directly by clients
REVOKE SELECT (registration_code_hash) ON public.messes FROM anon, authenticated;

-- Helper authorization functions (SECURITY DEFINER, search_path pinned)
CREATE OR REPLACE FUNCTION public.is_mess_admin(p_mess_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.mess_members mm
        JOIN public.profiles p ON p.id = mm.user_id
        WHERE mm.mess_id = p_mess_id
          AND mm.user_id = auth.uid()
          AND mm.member_role IN ('admin', 'owner')
          AND mm.membership_status = 'active'
          AND p.account_status = 'active'
    );
$$;

CREATE OR REPLACE FUNCTION public.is_mess_owner(p_mess_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.mess_members mm
        JOIN public.profiles p ON p.id = mm.user_id
        WHERE mm.mess_id = p_mess_id
          AND mm.user_id = auth.uid()
          AND mm.member_role = 'owner'
          AND mm.membership_status = 'active'
          AND p.account_status = 'active'
    );
$$;

CREATE OR REPLACE FUNCTION public.is_mess_active_member(p_mess_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.mess_members mm
        JOIN public.profiles p ON p.id = mm.user_id
        WHERE mm.mess_id = p_mess_id
          AND mm.user_id = auth.uid()
          AND mm.membership_status = 'active'
          AND p.account_status = 'active'
    );
$$;

CREATE OR REPLACE FUNCTION public.is_own_customer_profile(p_customer_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.customer_profiles cp
        JOIN public.mess_members mm ON mm.mess_id = cp.mess_id AND mm.user_id = auth.uid()
        WHERE cp.id = p_customer_id
          AND cp.user_id = auth.uid()
          AND cp.customer_status = 'active'
          AND mm.membership_status = 'active'
    );
$$;

-- Trigger guard: clients can never self-promote role or account_status on profiles
CREATE OR REPLACE FUNCTION public.guard_profile_privilege_columns()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF current_setting('role', true) NOT IN ('service_role', 'postgres', 'supabase_admin')
       AND current_setting('tiffin.trusted_rpc', true) IS DISTINCT FROM 'true' THEN
        IF NEW.role IS DISTINCT FROM OLD.role OR NEW.account_status IS DISTINCT FROM OLD.account_status THEN
            RAISE EXCEPTION 'Forbidden: role and account_status can only be changed server-side';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_profile_privilege ON public.profiles;
CREATE TRIGGER trg_guard_profile_privilege
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW EXECUTE FUNCTION public.guard_profile_privilege_columns();

-- Trigger guard: customers cannot change mess_id, user_id, or customer_status on customer_profiles
CREATE OR REPLACE FUNCTION public.guard_customer_profile_columns()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF current_setting('role', true) NOT IN ('service_role', 'postgres', 'supabase_admin')
       AND current_setting('tiffin.trusted_rpc', true) IS DISTINCT FROM 'true'
       AND NOT public.is_mess_admin(OLD.mess_id) THEN
        IF NEW.mess_id IS DISTINCT FROM OLD.mess_id
           OR NEW.user_id IS DISTINCT FROM OLD.user_id
           OR NEW.customer_status IS DISTINCT FROM OLD.customer_status THEN
            RAISE EXCEPTION 'Forbidden: cannot modify membership or status fields';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_customer_profile_columns ON public.customer_profiles;
CREATE TRIGGER trg_guard_customer_profile_columns
    BEFORE UPDATE ON public.customer_profiles
    FOR EACH ROW EXECUTE FUNCTION public.guard_customer_profile_columns();

-- RLS Policies

-- profiles
CREATE POLICY "Users read own profile" ON public.profiles
    FOR SELECT USING (id = auth.uid());

CREATE POLICY "Admins read profiles in own mess" ON public.profiles
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.mess_members mm
            WHERE mm.user_id = profiles.id AND public.is_mess_admin(mm.mess_id)
        )
    );

CREATE POLICY "Users update own allowed profile fields" ON public.profiles
    FOR UPDATE USING (id = auth.uid()) WITH CHECK (id = auth.uid());

-- messes (note: registration_code_hash column SELECT is revoked above)
CREATE POLICY "Members read own mess" ON public.messes
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.mess_members mm
            WHERE mm.mess_id = messes.id AND mm.user_id = auth.uid()
        )
    );

CREATE POLICY "Owners update own mess" ON public.messes
    FOR UPDATE USING (public.is_mess_owner(id)) WITH CHECK (public.is_mess_owner(id));

-- mess_members
CREATE POLICY "Users read own memberships" ON public.mess_members
    FOR SELECT USING (user_id = auth.uid() OR public.is_mess_admin(mess_id));

-- customer_profiles
CREATE POLICY "Customers read own customer profile" ON public.customer_profiles
    FOR SELECT USING (user_id = auth.uid() OR public.is_mess_admin(mess_id));

CREATE POLICY "Customers update own permitted fields" ON public.customer_profiles
    FOR UPDATE USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());

CREATE POLICY "Admins manage customer profiles in own mess" ON public.customer_profiles
    FOR ALL USING (public.is_mess_admin(mess_id)) WITH CHECK (public.is_mess_admin(mess_id));

-- registration_requests
CREATE POLICY "Users read own registration requests" ON public.registration_requests
    FOR SELECT USING (user_id = auth.uid() OR public.is_mess_admin(mess_id));

-- subscription_plans
CREATE POLICY "Active mess members read plans" ON public.subscription_plans
    FOR SELECT USING (public.is_mess_active_member(mess_id));

CREATE POLICY "Admins manage plans in own mess" ON public.subscription_plans
    FOR ALL USING (public.is_mess_admin(mess_id)) WITH CHECK (public.is_mess_admin(mess_id));

-- subscriptions
CREATE POLICY "Customers read own subscriptions" ON public.subscriptions
    FOR SELECT USING (public.is_own_customer_profile(customer_id) OR public.is_mess_admin(mess_id));

CREATE POLICY "Admins manage subscriptions in own mess" ON public.subscriptions
    FOR ALL USING (public.is_mess_admin(mess_id)) WITH CHECK (public.is_mess_admin(mess_id));

-- tiffin_records
CREATE POLICY "Customers read own tiffin records" ON public.tiffin_records
    FOR SELECT USING (public.is_own_customer_profile(customer_id) OR public.is_mess_admin(mess_id));

CREATE POLICY "Admins manage tiffin records in own mess" ON public.tiffin_records
    FOR ALL USING (public.is_mess_admin(mess_id)) WITH CHECK (public.is_mess_admin(mess_id));

-- daily_menus (customers ONLY see published menus; admins see draft & published)
CREATE POLICY "Active members read published menus" ON public.daily_menus
    FOR SELECT USING (
        (status = 'published' AND public.is_mess_active_member(mess_id))
        OR public.is_mess_admin(mess_id)
    );

CREATE POLICY "Admins manage menus in own mess" ON public.daily_menus
    FOR ALL USING (public.is_mess_admin(mess_id)) WITH CHECK (public.is_mess_admin(mess_id));

-- monthly_bills
CREATE POLICY "Customers read own bills" ON public.monthly_bills
    FOR SELECT USING (public.is_own_customer_profile(customer_id) OR public.is_mess_admin(mess_id));

CREATE POLICY "Admins manage bills in own mess" ON public.monthly_bills
    FOR ALL USING (public.is_mess_admin(mess_id)) WITH CHECK (public.is_mess_admin(mess_id));

-- payments (no physical DELETE allowed via RLS)
CREATE POLICY "Customers read own payments" ON public.payments
    FOR SELECT USING (public.is_own_customer_profile(customer_id) OR public.is_mess_admin(mess_id));

CREATE POLICY "Admins insert payments in own mess" ON public.payments
    FOR INSERT WITH CHECK (public.is_mess_admin(mess_id));

CREATE POLICY "Admins update payment status in own mess" ON public.payments
    FOR UPDATE USING (public.is_mess_admin(mess_id)) WITH CHECK (public.is_mess_admin(mess_id));

-- audit_logs
CREATE POLICY "Admins read audit logs of own mess" ON public.audit_logs
    FOR SELECT USING (public.is_mess_admin(mess_id));

-- subscription_renewal_requests
CREATE POLICY "Customers read and create own renewal requests" ON public.subscription_renewal_requests
    FOR SELECT USING (public.is_own_customer_profile(customer_id) OR public.is_mess_admin(mess_id));

CREATE POLICY "Customers insert own renewal requests" ON public.subscription_renewal_requests
    FOR INSERT WITH CHECK (public.is_own_customer_profile(customer_id));

CREATE POLICY "Admins manage renewal requests in own mess" ON public.subscription_renewal_requests
    FOR UPDATE USING (public.is_mess_admin(mess_id)) WITH CHECK (public.is_mess_admin(mess_id));

-- admin_invitations
CREATE POLICY "Owners manage admin invitations" ON public.admin_invitations
    FOR ALL USING (public.is_mess_owner(mess_id)) WITH CHECK (public.is_mess_owner(mess_id));

-- =============================================================================
-- TRUSTED SERVER-SIDE RPC FUNCTIONS
-- =============================================================================

-- 1. Resolve authenticated user context & claim pending admin/customer invites by verified email
CREATE OR REPLACE FUNCTION public.get_my_auth_context()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_profile public.profiles%ROWTYPE;
    v_membership public.mess_members%ROWTYPE;
    v_mess JSONB;
    v_customer public.customer_profiles%ROWTYPE;
    v_reg public.registration_requests%ROWTYPE;
    v_invite public.admin_invitations%ROWTYPE;
    v_invited_customer public.customer_profiles%ROWTYPE;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    PERFORM set_config('tiffin.trusted_rpc', 'true', true);

    SELECT * INTO v_profile FROM public.profiles WHERE id = v_uid;

    -- Claim pending admin invitation matching verified email
    SELECT * INTO v_invite
    FROM public.admin_invitations
    WHERE lower(invited_email) = lower(v_profile.email)
      AND status = 'pending'
    ORDER BY created_at DESC
    LIMIT 1;

    IF FOUND THEN
        UPDATE public.profiles SET role = 'admin', account_status = 'active' WHERE id = v_uid;
        INSERT INTO public.mess_members (mess_id, user_id, member_role, membership_status, joined_at)
        VALUES (v_invite.mess_id, v_uid, 'admin', 'active', now())
        ON CONFLICT (mess_id, user_id) DO UPDATE SET
            member_role = 'admin', membership_status = 'active', joined_at = now();
        UPDATE public.admin_invitations SET status = 'accepted' WHERE id = v_invite.id;
        INSERT INTO public.audit_logs (mess_id, actor_id, action, entity_type, entity_id, new_values)
        VALUES (v_invite.mess_id, v_uid, 'admin_invite_claimed', 'mess_members', v_uid, jsonb_build_object('email', v_profile.email));
        SELECT * INTO v_profile FROM public.profiles WHERE id = v_uid;
    END IF;

    -- Claim invited customer_profile created by admin matching verified email
    SELECT * INTO v_invited_customer
    FROM public.customer_profiles
    WHERE lower(email) = lower(v_profile.email)
      AND user_id IS NULL
      AND customer_status = 'active'
    ORDER BY created_at DESC
    LIMIT 1;

    IF FOUND THEN
        UPDATE public.customer_profiles SET user_id = v_uid WHERE id = v_invited_customer.id;
        INSERT INTO public.mess_members (mess_id, user_id, member_role, membership_status, joined_at)
        VALUES (v_invited_customer.mess_id, v_uid, 'customer', 'active', now())
        ON CONFLICT (mess_id, user_id) DO UPDATE SET
            membership_status = 'active', joined_at = COALESCE(public.mess_members.joined_at, now());
        IF v_profile.account_status = 'pending' THEN
            UPDATE public.profiles SET account_status = 'active' WHERE id = v_uid;
        END IF;
        SELECT * INTO v_profile FROM public.profiles WHERE id = v_uid;
    END IF;

    SELECT * INTO v_membership
    FROM public.mess_members
    WHERE user_id = v_uid
    ORDER BY CASE membership_status WHEN 'active' THEN 1 WHEN 'pending' THEN 2 ELSE 3 END, created_at DESC
    LIMIT 1;

    IF FOUND THEN
        SELECT jsonb_build_object(
            'id', m.id,
            'name', m.name,
            'owner_id', m.owner_id,
            'phone', m.phone,
            'address', m.address,
            'timezone', m.timezone,
            'currency', m.currency,
            'meal_slots', m.meal_slots,
            'default_due_day', m.default_due_day
        ) INTO v_mess
        FROM public.messes m WHERE m.id = v_membership.mess_id;

        SELECT * INTO v_customer
        FROM public.customer_profiles
        WHERE mess_id = v_membership.mess_id AND user_id = v_uid
        LIMIT 1;
    END IF;

    SELECT * INTO v_reg
    FROM public.registration_requests
    WHERE user_id = v_uid
    ORDER BY created_at DESC
    LIMIT 1;

    RETURN jsonb_build_object(
        'profile', to_jsonb(v_profile),
        'membership', CASE WHEN v_membership.id IS NOT NULL THEN to_jsonb(v_membership) ELSE NULL END,
        'mess', v_mess,
        'customer_profile', CASE WHEN v_customer.id IS NOT NULL THEN to_jsonb(v_customer) ELSE NULL END,
        'registration_request', CASE WHEN v_reg.id IS NOT NULL THEN to_jsonb(v_reg) ELSE NULL END
    );
END;
$$;

-- 2. Customer registration using mess registration code (never trusts client mess_id)
CREATE OR REPLACE FUNCTION public.submit_customer_registration(
    p_registration_code TEXT,
    p_full_name TEXT,
    p_phone TEXT,
    p_address_or_room TEXT DEFAULT ''
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_mess public.messes%ROWTYPE;
    v_req_id UUID;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;
    IF trim(coalesce(p_registration_code, '')) = '' THEN
        RAISE EXCEPTION 'Registration code is required';
    END IF;
    IF trim(coalesce(p_full_name, '')) = '' OR trim(coalesce(p_phone, '')) = '' THEN
        RAISE EXCEPTION 'Full name and mobile number are required';
    END IF;

    PERFORM set_config('tiffin.trusted_rpc', 'true', true);

    SELECT * INTO v_mess
    FROM public.messes
    WHERE registration_code_hash = crypt(trim(p_registration_code), registration_code_hash)
    LIMIT 1;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Invalid mess registration code';
    END IF;

    UPDATE public.profiles
    SET full_name = trim(p_full_name),
        phone = trim(p_phone),
        role = CASE WHEN role IN ('admin', 'owner') THEN role ELSE 'customer' END,
        account_status = CASE WHEN account_status = 'active' THEN 'active' ELSE 'pending' END
    WHERE id = v_uid;

    INSERT INTO public.mess_members (mess_id, user_id, member_role, membership_status)
    VALUES (v_mess.id, v_uid, 'customer', 'pending')
    ON CONFLICT (mess_id, user_id) DO UPDATE SET
        membership_status = CASE WHEN public.mess_members.membership_status = 'active' THEN 'active' ELSE 'pending' END;

    INSERT INTO public.registration_requests (mess_id, user_id, requested_name, phone, address_or_room, status)
    VALUES (v_mess.id, v_uid, trim(p_full_name), trim(p_phone), trim(coalesce(p_address_or_room, '')), 'pending')
    ON CONFLICT (mess_id, user_id) DO UPDATE SET
        requested_name = EXCLUDED.requested_name,
        phone = EXCLUDED.phone,
        address_or_room = EXCLUDED.address_or_room,
        status = 'pending',
        reviewed_by = NULL,
        reviewed_at = NULL
    RETURNING id INTO v_req_id;

    INSERT INTO public.audit_logs (mess_id, actor_id, action, entity_type, entity_id, new_values)
    VALUES (v_mess.id, v_uid, 'customer_registration_submitted', 'registration_requests', v_req_id,
            jsonb_build_object('requested_name', trim(p_full_name), 'phone', trim(p_phone)));

    RETURN jsonb_build_object('request_id', v_req_id, 'mess_name', v_mess.name, 'status', 'pending');
END;
$$;

-- 3. Admin approves or rejects a customer registration request
CREATE OR REPLACE FUNCTION public.review_registration_request(
    p_request_id UUID,
    p_decision TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_req public.registration_requests%ROWTYPE;
    v_prof public.profiles%ROWTYPE;
    v_cust_id UUID;
BEGIN
    IF p_decision NOT IN ('approved', 'rejected') THEN
        RAISE EXCEPTION 'Decision must be approved or rejected';
    END IF;

    PERFORM set_config('tiffin.trusted_rpc', 'true', true);

    SELECT * INTO v_req FROM public.registration_requests WHERE id = p_request_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Registration request not found';
    END IF;
    IF NOT public.is_mess_admin(v_req.mess_id) THEN
        RAISE EXCEPTION 'Unauthorized: admin access required for this mess';
    END IF;

    SELECT * INTO v_prof FROM public.profiles WHERE id = v_req.user_id;

    UPDATE public.registration_requests
    SET status = p_decision, reviewed_by = v_uid, reviewed_at = now()
    WHERE id = p_request_id;

    IF p_decision = 'approved' THEN
        UPDATE public.profiles SET account_status = 'active', role = CASE WHEN role IN ('admin', 'owner') THEN role ELSE 'customer' END
        WHERE id = v_req.user_id;

        UPDATE public.mess_members
        SET membership_status = 'active', joined_at = now()
        WHERE mess_id = v_req.mess_id AND user_id = v_req.user_id;

        INSERT INTO public.customer_profiles (mess_id, user_id, full_name, phone, email, address_or_room, customer_status)
        VALUES (v_req.mess_id, v_req.user_id, v_req.requested_name, v_req.phone, v_prof.email, v_req.address_or_room, 'active')
        ON CONFLICT (mess_id, user_id) DO UPDATE SET
            full_name = EXCLUDED.full_name,
            phone = EXCLUDED.phone,
            address_or_room = EXCLUDED.address_or_room,
            customer_status = 'active'
        RETURNING id INTO v_cust_id;
    ELSE
        UPDATE public.profiles SET account_status = 'rejected' WHERE id = v_req.user_id AND account_status = 'pending';
        UPDATE public.mess_members SET membership_status = 'rejected' WHERE mess_id = v_req.mess_id AND user_id = v_req.user_id;
    END IF;

    INSERT INTO public.audit_logs (mess_id, actor_id, action, entity_type, entity_id, new_values)
    VALUES (v_req.mess_id, v_uid, 'registration_request_' || p_decision, 'registration_requests', p_request_id,
            jsonb_build_object('user_id', v_req.user_id, 'customer_id', v_cust_id));

    RETURN jsonb_build_object('status', p_decision, 'customer_id', v_cust_id);
END;
$$;

-- 4. Admin creates / links a customer cleanly without creating fake auth users
CREATE OR REPLACE FUNCTION public.admin_create_customer(
    p_mess_id UUID,
    p_full_name TEXT,
    p_phone TEXT,
    p_email TEXT DEFAULT NULL,
    p_address_or_room TEXT DEFAULT ''
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_existing_user_id UUID;
    v_cust public.customer_profiles%ROWTYPE;
BEGIN
    IF NOT public.is_mess_admin(p_mess_id) THEN
        RAISE EXCEPTION 'Unauthorized';
    END IF;

    PERFORM set_config('tiffin.trusted_rpc', 'true', true);

    IF coalesce(trim(p_email), '') <> '' THEN
        SELECT id INTO v_existing_user_id
        FROM public.profiles
        WHERE lower(email) = lower(trim(p_email))
        LIMIT 1;
    END IF;

    INSERT INTO public.customer_profiles (mess_id, user_id, full_name, phone, email, address_or_room, customer_status)
    VALUES (p_mess_id, v_existing_user_id, trim(p_full_name), trim(p_phone), NULLIF(lower(trim(p_email)), ''), trim(coalesce(p_address_or_room, '')), 'active')
    RETURNING * INTO v_cust;

    IF v_existing_user_id IS NOT NULL THEN
        INSERT INTO public.mess_members (mess_id, user_id, member_role, membership_status, joined_at)
        VALUES (p_mess_id, v_existing_user_id, 'customer', 'active', now())
        ON CONFLICT (mess_id, user_id) DO UPDATE SET membership_status = 'active', joined_at = COALESCE(public.mess_members.joined_at, now());

        UPDATE public.profiles SET account_status = 'active' WHERE id = v_existing_user_id AND account_status = 'pending';
    END IF;

    INSERT INTO public.audit_logs (mess_id, actor_id, action, entity_type, entity_id, new_values)
    VALUES (p_mess_id, v_uid, 'customer_created', 'customer_profiles', v_cust.id, to_jsonb(v_cust));

    RETURN to_jsonb(v_cust);
END;
$$;

-- 5. Atomic tiffin delivery record upsert with audit log
CREATE OR REPLACE FUNCTION public.upsert_tiffin_record(
    p_customer_id UUID,
    p_service_date DATE,
    p_meal_slot TEXT,
    p_status TEXT,
    p_note TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_cust public.customer_profiles%ROWTYPE;
    v_sub_id UUID;
    v_old public.tiffin_records%ROWTYPE;
    v_new public.tiffin_records%ROWTYPE;
BEGIN
    SELECT * INTO v_cust FROM public.customer_profiles WHERE id = p_customer_id;
    IF NOT FOUND OR NOT public.is_mess_admin(v_cust.mess_id) THEN
        RAISE EXCEPTION 'Unauthorized or customer not found';
    END IF;

    SELECT id INTO v_sub_id
    FROM public.subscriptions
    WHERE customer_id = p_customer_id
      AND status IN ('active', 'expiring_soon')
      AND p_service_date BETWEEN start_date AND end_date
    ORDER BY created_at DESC
    LIMIT 1;

    SELECT * INTO v_old
    FROM public.tiffin_records
    WHERE mess_id = v_cust.mess_id
      AND customer_id = p_customer_id
      AND service_date = p_service_date
      AND meal_slot = p_meal_slot;

    INSERT INTO public.tiffin_records (
        mess_id, customer_id, subscription_id, service_date, meal_slot, status, delivered_at, marked_by, note
    )
    VALUES (
        v_cust.mess_id,
        p_customer_id,
        v_sub_id,
        p_service_date,
        p_meal_slot,
        p_status,
        CASE WHEN p_status = 'delivered' THEN now() ELSE NULL END,
        v_uid,
        p_note
    )
    ON CONFLICT (mess_id, customer_id, service_date, meal_slot) DO UPDATE SET
        subscription_id = COALESCE(EXCLUDED.subscription_id, public.tiffin_records.subscription_id),
        status = EXCLUDED.status,
        delivered_at = CASE WHEN EXCLUDED.status = 'delivered' THEN COALESCE(public.tiffin_records.delivered_at, now()) ELSE NULL END,
        marked_by = v_uid,
        note = EXCLUDED.note
    RETURNING * INTO v_new;

    INSERT INTO public.audit_logs (mess_id, actor_id, action, entity_type, entity_id, old_values, new_values)
    VALUES (
        v_cust.mess_id, v_uid,
        CASE WHEN v_old.id IS NULL THEN 'tiffin_marked' ELSE 'tiffin_corrected' END,
        'tiffin_records', v_new.id,
        CASE WHEN v_old.id IS NOT NULL THEN to_jsonb(v_old) ELSE NULL END,
        to_jsonb(v_new)
    );

    RETURN to_jsonb(v_new);
END;
$$;

-- 6. Bulk mark active customers delivered
CREATE OR REPLACE FUNCTION public.bulk_mark_tiffin_delivered(
    p_mess_id UUID,
    p_customer_ids UUID[],
    p_service_date DATE,
    p_meal_slot TEXT,
    p_note TEXT DEFAULT NULL
)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_cid UUID;
    v_count INTEGER := 0;
BEGIN
    IF NOT public.is_mess_admin(p_mess_id) THEN
        RAISE EXCEPTION 'Unauthorized';
    END IF;

    FOREACH v_cid IN ARRAY p_customer_ids
    LOOP
        IF EXISTS (SELECT 1 FROM public.customer_profiles WHERE id = v_cid AND mess_id = p_mess_id AND customer_status = 'active') THEN
            PERFORM public.upsert_tiffin_record(v_cid, p_service_date, p_meal_slot, 'delivered', p_note);
            v_count := v_count + 1;
        END IF;
    END LOOP;

    RETURN v_count;
END;
$$;

-- 7. Generate or recalculate monthly bill atomically using mess timezone
CREATE OR REPLACE FUNCTION public.generate_monthly_bill(
    p_customer_id UUID,
    p_billing_month DATE,
    p_discount_amount NUMERIC DEFAULT 0,
    p_previous_balance NUMERIC DEFAULT 0
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_cust public.customer_profiles%ROWTYPE;
    v_mess public.messes%ROWTYPE;
    v_month_start DATE := date_trunc('month', p_billing_month)::date;
    v_month_end DATE := (date_trunc('month', p_billing_month) + interval '1 month - 1 day')::date;
    v_sub public.subscriptions%ROWTYPE;
    v_delivered_count INTEGER := 0;
    v_extra_count INTEGER := 0;
    v_extra_charge NUMERIC(12, 2) := 0;
    v_sub_charge NUMERIC(12, 2) := 0;
    v_total NUMERIC(12, 2) := 0;
    v_paid NUMERIC(12, 2) := 0;
    v_balance NUMERIC(12, 2) := 0;
    v_due_date DATE;
    v_today DATE;
    v_status TEXT;
    v_bill public.monthly_bills%ROWTYPE;
BEGIN
    SELECT * INTO v_cust FROM public.customer_profiles WHERE id = p_customer_id;
    IF NOT FOUND OR NOT public.is_mess_admin(v_cust.mess_id) THEN
        RAISE EXCEPTION 'Unauthorized or customer not found';
    END IF;

    SELECT * INTO v_mess FROM public.messes WHERE id = v_cust.mess_id;
    v_today := (now() AT TIME ZONE v_mess.timezone)::date;
    v_due_date := (v_month_start + (v_mess.default_due_day - 1) * interval '1 day')::date;

    SELECT * INTO v_sub
    FROM public.subscriptions
    WHERE customer_id = p_customer_id
      AND status IN ('active', 'expiring_soon', 'expired')
      AND start_date <= v_month_end
      AND end_date >= v_month_start
    ORDER BY created_at DESC
    LIMIT 1;

    SELECT count(*)::integer INTO v_delivered_count
    FROM public.tiffin_records
    WHERE customer_id = p_customer_id
      AND status = 'delivered'
      AND service_date BETWEEN v_month_start AND v_month_end;

    IF v_sub.id IS NOT NULL THEN
        v_sub_charge := v_sub.agreed_price;
        IF v_sub.included_tiffins > 0 AND v_delivered_count > v_sub.included_tiffins THEN
            v_extra_count := v_delivered_count - v_sub.included_tiffins;
            v_extra_charge := round(v_extra_count * v_sub.extra_tiffin_price_snapshot, 2);
        END IF;
    END IF;

    v_total := GREATEST(0, round(v_sub_charge + v_extra_charge + coalesce(p_previous_balance, 0) - coalesce(p_discount_amount, 0), 2));

    SELECT coalesce(sum(amount), 0) INTO v_paid
    FROM public.payments
    WHERE customer_id = p_customer_id
      AND mess_id = v_cust.mess_id
      AND status = 'confirmed'
      AND bill_id IN (
          SELECT id FROM public.monthly_bills
          WHERE customer_id = p_customer_id AND billing_month = v_month_start
      );

    v_balance := GREATEST(0, round(v_total - v_paid, 2));

    IF v_balance = 0 THEN
        v_status := 'paid';
    ELSIF v_today > v_due_date THEN
        v_status := 'overdue';
    ELSIF v_paid > 0 THEN
        v_status := 'partially_paid';
    ELSE
        v_status := 'pending';
    END IF;

    INSERT INTO public.monthly_bills (
        mess_id, customer_id, subscription_id, billing_month,
        subscription_charge, extra_tiffin_count, extra_tiffin_charge,
        discount_amount, previous_balance, total_amount, amount_paid, balance_due,
        due_date, status, generated_at
    )
    VALUES (
        v_cust.mess_id, p_customer_id, v_sub.id, v_month_start,
        v_sub_charge, v_extra_count, v_extra_charge,
        coalesce(p_discount_amount, 0), coalesce(p_previous_balance, 0), v_total, v_paid, v_balance,
        v_due_date, v_status, now()
    )
    ON CONFLICT (mess_id, customer_id, billing_month) DO UPDATE SET
        subscription_id = EXCLUDED.subscription_id,
        subscription_charge = EXCLUDED.subscription_charge,
        extra_tiffin_count = EXCLUDED.extra_tiffin_count,
        extra_tiffin_charge = EXCLUDED.extra_tiffin_charge,
        discount_amount = EXCLUDED.discount_amount,
        previous_balance = EXCLUDED.previous_balance,
        total_amount = EXCLUDED.total_amount,
        amount_paid = EXCLUDED.amount_paid,
        balance_due = EXCLUDED.balance_due,
        due_date = EXCLUDED.due_date,
        status = EXCLUDED.status,
        generated_at = now()
    RETURNING * INTO v_bill;

    INSERT INTO public.audit_logs (mess_id, actor_id, action, entity_type, entity_id, new_values)
    VALUES (v_cust.mess_id, v_uid, 'bill_generated', 'monthly_bills', v_bill.id, to_jsonb(v_bill));

    RETURN to_jsonb(v_bill);
END;
$$;

-- 8. Record confirmed customer payment & recalculate bill balance atomically
CREATE OR REPLACE FUNCTION public.record_customer_payment(
    p_bill_id UUID,
    p_amount NUMERIC,
    p_payment_method TEXT,
    p_reference_note TEXT DEFAULT NULL,
    p_payment_date TIMESTAMPTZ DEFAULT now()
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_bill public.monthly_bills%ROWTYPE;
    v_mess public.messes%ROWTYPE;
    v_payment public.payments%ROWTYPE;
    v_paid NUMERIC(12, 2);
    v_balance NUMERIC(12, 2);
    v_today DATE;
    v_status TEXT;
BEGIN
    SELECT * INTO v_bill FROM public.monthly_bills WHERE id = p_bill_id FOR UPDATE;
    IF NOT FOUND OR NOT public.is_mess_admin(v_bill.mess_id) THEN
        RAISE EXCEPTION 'Unauthorized or bill not found';
    END IF;
    IF p_amount <= 0 THEN
        RAISE EXCEPTION 'Payment amount must be greater than zero';
    END IF;
    IF p_amount > v_bill.balance_due THEN
        RAISE EXCEPTION 'Payment exceeds remaining balance due (%).', v_bill.balance_due;
    END IF;

    SELECT * INTO v_mess FROM public.messes WHERE id = v_bill.mess_id;
    v_today := (now() AT TIME ZONE v_mess.timezone)::date;

    INSERT INTO public.payments (
        mess_id, customer_id, bill_id, amount, payment_method, payment_date, reference_note, recorded_by, status
    )
    VALUES (
        v_bill.mess_id, v_bill.customer_id, p_bill_id, round(p_amount, 2), p_payment_method,
        coalesce(p_payment_date, now()), p_reference_note, v_uid, 'confirmed'
    )
    RETURNING * INTO v_payment;

    SELECT coalesce(sum(amount), 0) INTO v_paid
    FROM public.payments WHERE bill_id = p_bill_id AND status = 'confirmed';

    v_balance := GREATEST(0, round(v_bill.total_amount - v_paid, 2));
    IF v_balance = 0 THEN
        v_status := 'paid';
    ELSIF v_today > v_bill.due_date THEN
        v_status := 'overdue';
    ELSIF v_paid > 0 THEN
        v_status := 'partially_paid';
    ELSE
        v_status := 'pending';
    END IF;

    UPDATE public.monthly_bills
    SET amount_paid = v_paid, balance_due = v_balance, status = v_status
    WHERE id = p_bill_id;

    INSERT INTO public.audit_logs (mess_id, actor_id, action, entity_type, entity_id, new_values)
    VALUES (v_bill.mess_id, v_uid, 'payment_recorded', 'payments', v_payment.id, to_jsonb(v_payment));

    RETURN to_jsonb(v_payment);
END;
$$;

-- 9. Reverse a payment without deleting financial history
CREATE OR REPLACE FUNCTION public.reverse_payment(
    p_payment_id UUID,
    p_reason TEXT DEFAULT 'Admin reversal'
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_payment public.payments%ROWTYPE;
    v_bill public.monthly_bills%ROWTYPE;
    v_mess public.messes%ROWTYPE;
    v_paid NUMERIC(12, 2);
    v_balance NUMERIC(12, 2);
    v_today DATE;
    v_status TEXT;
BEGIN
    SELECT * INTO v_payment FROM public.payments WHERE id = p_payment_id FOR UPDATE;
    IF NOT FOUND OR NOT public.is_mess_admin(v_payment.mess_id) THEN
        RAISE EXCEPTION 'Unauthorized or payment not found';
    END IF;
    IF v_payment.status = 'reversed' THEN
        RAISE EXCEPTION 'Payment is already reversed';
    END IF;

    UPDATE public.payments
    SET status = 'reversed',
        reference_note = concat_ws(' | ', reference_note, 'REVERSED: ' || coalesce(p_reason, 'Admin reversal'))
    WHERE id = p_payment_id
    RETURNING * INTO v_payment;

    SELECT * INTO v_bill FROM public.monthly_bills WHERE id = v_payment.bill_id FOR UPDATE;
    SELECT * INTO v_mess FROM public.messes WHERE id = v_bill.mess_id;
    v_today := (now() AT TIME ZONE v_mess.timezone)::date;

    SELECT coalesce(sum(amount), 0) INTO v_paid
    FROM public.payments WHERE bill_id = v_bill.id AND status = 'confirmed';

    v_balance := GREATEST(0, round(v_bill.total_amount - v_paid, 2));
    IF v_balance = 0 THEN
        v_status := 'paid';
    ELSIF v_today > v_bill.due_date THEN
        v_status := 'overdue';
    ELSIF v_paid > 0 THEN
        v_status := 'partially_paid';
    ELSE
        v_status := 'pending';
    END IF;

    UPDATE public.monthly_bills
    SET amount_paid = v_paid, balance_due = v_balance, status = v_status
    WHERE id = v_bill.id;

    INSERT INTO public.audit_logs (mess_id, actor_id, action, entity_type, entity_id, new_values)
    VALUES (v_bill.mess_id, v_uid, 'payment_reversed', 'payments', p_payment_id, to_jsonb(v_payment));

    RETURN to_jsonb(v_payment);
END;
$$;

-- 10. Owner-only settings RPCs
CREATE OR REPLACE FUNCTION public.owner_update_registration_code(
    p_mess_id UUID,
    p_new_code TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NOT public.is_mess_owner(p_mess_id) THEN
        RAISE EXCEPTION 'Only the mess owner can update the registration code';
    END IF;
    IF length(trim(coalesce(p_new_code, ''))) < 4 THEN
        RAISE EXCEPTION 'Registration code must be at least 4 characters';
    END IF;

    UPDATE public.messes
    SET registration_code_hash = crypt(trim(p_new_code), gen_salt('bf'))
    WHERE id = p_mess_id;

    INSERT INTO public.audit_logs (mess_id, actor_id, action, entity_type, entity_id, new_values)
    VALUES (p_mess_id, auth.uid(), 'registration_code_rotated', 'messes', p_mess_id, jsonb_build_object('rotated', true));

    RETURN true;
END;
$$;

CREATE OR REPLACE FUNCTION public.owner_invite_admin(
    p_mess_id UUID,
    p_email TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_invite public.admin_invitations%ROWTYPE;
BEGIN
    IF NOT public.is_mess_owner(p_mess_id) THEN
        RAISE EXCEPTION 'Only the mess owner can invite admins';
    END IF;

    INSERT INTO public.admin_invitations (mess_id, invited_email, invited_role, status, invited_by)
    VALUES (p_mess_id, lower(trim(p_email)), 'admin', 'pending', auth.uid())
    ON CONFLICT (mess_id, invited_email) DO UPDATE SET
        status = 'pending', invited_by = auth.uid()
    RETURNING * INTO v_invite;

    INSERT INTO public.audit_logs (mess_id, actor_id, action, entity_type, entity_id, new_values)
    VALUES (p_mess_id, auth.uid(), 'admin_invited', 'admin_invitations', v_invite.id, to_jsonb(v_invite));

    RETURN to_jsonb(v_invite);
END;
$$;

-- 11. Initial owner provisioning (SERVICE_ROLE / SQL EDITOR ONLY — NEVER callable by clients)
CREATE OR REPLACE FUNCTION public.provision_initial_owner(
    p_user_email TEXT,
    p_mess_name TEXT,
    p_registration_code TEXT,
    p_phone TEXT DEFAULT '',
    p_address TEXT DEFAULT '',
    p_timezone TEXT DEFAULT 'Asia/Kolkata'
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
    v_mess_id UUID;
BEGIN
    PERFORM set_config('tiffin.trusted_rpc', 'true', true);

    SELECT id INTO v_user_id
    FROM public.profiles
    WHERE lower(email) = lower(trim(p_user_email))
    LIMIT 1;

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'User % must sign in with Google at least once before owner provisioning', p_user_email;
    END IF;

    UPDATE public.profiles
    SET role = 'owner', account_status = 'active'
    WHERE id = v_user_id;

    INSERT INTO public.messes (name, owner_id, phone, address, timezone, registration_code_hash)
    VALUES (
        trim(p_mess_name),
        v_user_id,
        coalesce(p_phone, ''),
        coalesce(p_address, ''),
        coalesce(p_timezone, 'Asia/Kolkata'),
        crypt(trim(p_registration_code), gen_salt('bf'))
    )
    RETURNING id INTO v_mess_id;

    INSERT INTO public.mess_members (mess_id, user_id, member_role, membership_status, joined_at)
    VALUES (v_mess_id, v_user_id, 'owner', 'active', now())
    ON CONFLICT (mess_id, user_id) DO UPDATE SET
        member_role = 'owner', membership_status = 'active', joined_at = now();

    INSERT INTO public.audit_logs (mess_id, actor_id, action, entity_type, entity_id, new_values)
    VALUES (v_mess_id, v_user_id, 'owner_provisioned', 'messes', v_mess_id, jsonb_build_object('mess_name', p_mess_name));

    RETURN jsonb_build_object('mess_id', v_mess_id, 'owner_id', v_user_id, 'status', 'owner_active');
END;
$$;

REVOKE ALL ON FUNCTION public.provision_initial_owner(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.provision_initial_owner(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) TO service_role;

-- Enable Supabase Realtime on live-updating tables
DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.daily_menus;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.tiffin_records;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.monthly_bills;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.registration_requests;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
