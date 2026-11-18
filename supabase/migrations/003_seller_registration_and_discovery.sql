-- Tiffin Mess — Migration 003: Seller Self-Registration, Customer Location, Discovery & Ratings
-- Apply after 001_initial_schema.sql and 002_rls_and_functions.sql.

-- 1. Extend messes for multi-mess discovery & seller details
ALTER TABLE public.messes
    ADD COLUMN IF NOT EXISTS area TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS city TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS veg_type TEXT NOT NULL DEFAULT 'both',
    ADD COLUMN IF NOT EXISTS monthly_price NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS lunch_time TEXT DEFAULT '',
    ADD COLUMN IF NOT EXISTS dinner_time TEXT DEFAULT '',
    ADD COLUMN IF NOT EXISTS delivery_areas TEXT DEFAULT '',
    ADD COLUMN IF NOT EXISTS description TEXT DEFAULT '',
    ADD COLUMN IF NOT EXISTS phone_public TEXT DEFAULT '',
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS avg_rating NUMERIC(3, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rating_count INTEGER NOT NULL DEFAULT 0;

DO $$
BEGIN
    ALTER TABLE public.messes
        ADD CONSTRAINT chk_messes_veg_type CHECK (veg_type IN ('veg', 'non-veg', 'both'));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE public.messes
        ADD CONSTRAINT chk_messes_monthly_price CHECK (monthly_price IS NULL OR monthly_price >= 0);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS idx_messes_active_city_area
    ON public.messes (is_active, lower(city), lower(area));

-- 2. Extend profiles with customer location (area + city)
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS area TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS city TEXT NOT NULL DEFAULT '';

-- 3. Ratings & reviews table
CREATE TABLE IF NOT EXISTS public.mess_ratings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mess_id UUID NOT NULL REFERENCES public.messes(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    stars INTEGER NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment TEXT DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (mess_id, customer_id)
);

CREATE INDEX IF NOT EXISTS idx_mess_ratings_mess ON public.mess_ratings (mess_id, created_at DESC);

DROP TRIGGER IF EXISTS trg_mess_ratings_updated_at ON public.mess_ratings;
CREATE TRIGGER trg_mess_ratings_updated_at
    BEFORE UPDATE ON public.mess_ratings
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- Recompute avg_rating & rating_count on messes automatically
CREATE OR REPLACE FUNCTION public.recompute_mess_rating()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_mess_id UUID;
BEGIN
    v_mess_id := COALESCE(NEW.mess_id, OLD.mess_id);
    UPDATE public.messes m
    SET
        rating_count = COALESCE((SELECT count(*)::integer FROM public.mess_ratings r WHERE r.mess_id = v_mess_id), 0),
        avg_rating = COALESCE((SELECT round(avg(r.stars)::numeric, 2) FROM public.mess_ratings r WHERE r.mess_id = v_mess_id), 0)
    WHERE m.id = v_mess_id;
    RETURN COALESCE(NEW, OLD);
END;
$$;

DROP TRIGGER IF EXISTS trg_recompute_mess_rating ON public.mess_ratings;
CREATE TRIGGER trg_recompute_mess_rating
    AFTER INSERT OR UPDATE OR DELETE ON public.mess_ratings
    FOR EACH ROW EXECUTE FUNCTION public.recompute_mess_rating();

-- 4. RLS for discovery & ratings
ALTER TABLE public.mess_ratings ENABLE ROW LEVEL SECURITY;

-- Allow any authenticated user to browse active messes (registration_code_hash remains revoked from 002)
DO $$
BEGIN
    CREATE POLICY "Authenticated users read active messes for discovery"
        ON public.messes
        FOR SELECT
        TO authenticated
        USING (is_active = true OR public.is_mess_admin(id));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- Allow any authenticated user to read published menus for discovery previews (drafts stay hidden)
DO $$
BEGIN
    CREATE POLICY "Authenticated users read published menus for discovery"
        ON public.daily_menus
        FOR SELECT
        TO authenticated
        USING (status = 'published');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- Ratings policies: authenticated can read, only active mess members can insert/update their own rating
DO $$
BEGIN
    CREATE POLICY "Authenticated users read mess ratings"
        ON public.mess_ratings
        FOR SELECT
        TO authenticated
        USING (true);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE POLICY "Active mess members insert own rating"
        ON public.mess_ratings
        FOR INSERT
        TO authenticated
        WITH CHECK (customer_id = auth.uid() AND public.is_mess_active_member(mess_id));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE POLICY "Active mess members update own rating"
        ON public.mess_ratings
        FOR UPDATE
        TO authenticated
        USING (customer_id = auth.uid() AND public.is_mess_active_member(mess_id))
        WITH CHECK (customer_id = auth.uid() AND public.is_mess_active_member(mess_id));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- Safe RPC to fetch ratings with reviewer display name (never exposes emails/phones)
CREATE OR REPLACE FUNCTION public.get_mess_reviews(p_mess_id UUID)
RETURNS TABLE (
    id UUID,
    mess_id UUID,
    customer_id UUID,
    reviewer_name TEXT,
    stars INTEGER,
    comment TEXT,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT
        r.id,
        r.mess_id,
        r.customer_id,
        COALESCE(NULLIF(trim(p.full_name), ''), 'Customer') AS reviewer_name,
        r.stars,
        COALESCE(r.comment, '') AS comment,
        r.created_at,
        r.updated_at
    FROM public.mess_ratings r
    JOIN public.profiles p ON p.id = r.customer_id
    WHERE r.mess_id = p_mess_id
    ORDER BY r.updated_at DESC;
$$;

REVOKE ALL ON FUNCTION public.get_mess_reviews(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_mess_reviews(UUID) TO authenticated;

-- 5. Seller Self-Registration RPC (SECURITY DEFINER, authenticated only)
CREATE OR REPLACE FUNCTION public.register_my_mess(
    p_mess_name TEXT,
    p_area TEXT,
    p_city TEXT,
    p_phone TEXT,
    p_veg_type TEXT,
    p_monthly_price NUMERIC,
    p_lunch_time TEXT DEFAULT '',
    p_dinner_time TEXT DEFAULT '',
    p_delivery_areas TEXT DEFAULT '',
    p_description TEXT DEFAULT ''
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_name TEXT := trim(coalesce(p_mess_name, ''));
    v_area TEXT := trim(coalesce(p_area, ''));
    v_city TEXT := trim(coalesce(p_city, ''));
    v_phone TEXT := trim(coalesce(p_phone, ''));
    v_veg TEXT := lower(trim(coalesce(p_veg_type, '')));
    v_alphabet TEXT := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    v_bytes BYTEA;
    v_code TEXT := '';
    v_i INTEGER;
    v_mess_id UUID;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;
    IF v_name = '' THEN
        RAISE EXCEPTION 'Mess name is required';
    END IF;
    IF v_area = '' OR v_city = '' THEN
        RAISE EXCEPTION 'Area and city are required';
    END IF;
    IF v_phone = '' THEN
        RAISE EXCEPTION 'Mobile number is required';
    END IF;

    IF v_veg = 'non_veg' THEN
        v_veg := 'non-veg';
    END IF;
    IF v_veg NOT IN ('veg', 'non-veg', 'both') THEN
        RAISE EXCEPTION 'Veg type must be veg, non-veg, or both';
    END IF;
    IF p_monthly_price IS NULL OR p_monthly_price < 0 THEN
        RAISE EXCEPTION 'Monthly price must be 0 or greater';
    END IF;

    -- One mess per owner account
    IF EXISTS (SELECT 1 FROM public.messes WHERE owner_id = v_uid)
       OR EXISTS (SELECT 1 FROM public.mess_members WHERE user_id = v_uid AND member_role IN ('owner', 'admin') AND membership_status = 'active') THEN
        RAISE EXCEPTION 'This account already manages a registered mess';
    END IF;

    -- Reject duplicate mess name in the same area + city
    IF EXISTS (
        SELECT 1 FROM public.messes
        WHERE lower(trim(name)) = lower(v_name)
          AND lower(trim(area)) = lower(v_area)
          AND lower(trim(city)) = lower(v_city)
    ) THEN
        RAISE EXCEPTION 'A mess named "%" already exists in %, %', v_name, v_area, v_city;
    END IF;

    -- Generate 6-char uppercase alphanumeric registration code
    v_bytes := gen_random_bytes(6);
    FOR v_i IN 0..5 LOOP
        v_code := v_code || substr(v_alphabet, (get_byte(v_bytes, v_i) % length(v_alphabet)) + 1, 1);
    END LOOP;

    PERFORM set_config('tiffin.trusted_rpc', 'true', true);

    UPDATE public.profiles
    SET role = 'owner',
        account_status = 'active',
        phone = CASE WHEN phone = '' THEN v_phone ELSE phone END,
        area = v_area,
        city = v_city
    WHERE id = v_uid;

    INSERT INTO public.messes (
        name, owner_id, phone, phone_public, address, area, city,
        veg_type, monthly_price, lunch_time, dinner_time,
        delivery_areas, description, registration_code_hash, is_active
    )
    VALUES (
        v_name,
        v_uid,
        v_phone,
        v_phone,
        v_area || ', ' || v_city,
        v_area,
        v_city,
        v_veg,
        round(p_monthly_price, 2),
        trim(coalesce(p_lunch_time, '')),
        trim(coalesce(p_dinner_time, '')),
        trim(coalesce(p_delivery_areas, '')),
        trim(coalesce(p_description, '')),
        crypt(v_code, gen_salt('bf')),
        true
    )
    RETURNING id INTO v_mess_id;

    INSERT INTO public.mess_members (mess_id, user_id, member_role, membership_status, joined_at)
    VALUES (v_mess_id, v_uid, 'owner', 'active', now())
    ON CONFLICT (mess_id, user_id) DO UPDATE SET
        member_role = 'owner',
        membership_status = 'active',
        joined_at = now();

    INSERT INTO public.audit_logs (mess_id, actor_id, action, entity_type, entity_id, new_values)
    VALUES (
        v_mess_id,
        v_uid,
        'mess_self_registered',
        'messes',
        v_mess_id,
        jsonb_build_object('name', v_name, 'area', v_area, 'city', v_city)
    );

    RETURN jsonb_build_object(
        'mess_id', v_mess_id,
        'mess_name', v_name,
        'registration_code', v_code
    );
END;
$$;

REVOKE ALL ON FUNCTION public.register_my_mess(TEXT, TEXT, TEXT, TEXT, TEXT, NUMERIC, TEXT, TEXT, TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.register_my_mess(TEXT, TEXT, TEXT, TEXT, TEXT, NUMERIC, TEXT, TEXT, TEXT, TEXT) TO authenticated;

-- 6. Update get_my_auth_context() to include new mess & profile fields
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
            'phone_public', m.phone_public,
            'address', m.address,
            'area', m.area,
            'city', m.city,
            'veg_type', m.veg_type,
            'monthly_price', m.monthly_price,
            'lunch_time', m.lunch_time,
            'dinner_time', m.dinner_time,
            'delivery_areas', m.delivery_areas,
            'description', m.description,
            'is_active', m.is_active,
            'avg_rating', m.avg_rating,
            'rating_count', m.rating_count,
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

-- 7. Update submit_customer_registration to also save area & city on profiles,
--    plus submit_customer_registration_by_mess for joining directly from Discovery.
DROP FUNCTION IF EXISTS public.submit_customer_registration(TEXT, TEXT, TEXT, TEXT);

CREATE OR REPLACE FUNCTION public.submit_customer_registration(
    p_registration_code TEXT,
    p_full_name TEXT,
    p_phone TEXT,
    p_address_or_room TEXT DEFAULT '',
    p_area TEXT DEFAULT '',
    p_city TEXT DEFAULT ''
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
        area = CASE WHEN trim(coalesce(p_area, '')) <> '' THEN trim(p_area) ELSE area END,
        city = CASE WHEN trim(coalesce(p_city, '')) <> '' THEN trim(p_city) ELSE city END,
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

REVOKE ALL ON FUNCTION public.submit_customer_registration(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.submit_customer_registration(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT) TO authenticated;

CREATE OR REPLACE FUNCTION public.submit_customer_registration_by_mess(
    p_mess_id UUID,
    p_full_name TEXT,
    p_phone TEXT,
    p_address_or_room TEXT DEFAULT '',
    p_area TEXT DEFAULT '',
    p_city TEXT DEFAULT ''
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
    IF trim(coalesce(p_full_name, '')) = '' OR trim(coalesce(p_phone, '')) = '' THEN
        RAISE EXCEPTION 'Full name and mobile number are required';
    END IF;

    SELECT * INTO v_mess FROM public.messes WHERE id = p_mess_id AND is_active = true;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Selected mess not found or inactive';
    END IF;

    PERFORM set_config('tiffin.trusted_rpc', 'true', true);

    UPDATE public.profiles
    SET full_name = trim(p_full_name),
        phone = trim(p_phone),
        area = CASE WHEN trim(coalesce(p_area, '')) <> '' THEN trim(p_area) ELSE area END,
        city = CASE WHEN trim(coalesce(p_city, '')) <> '' THEN trim(p_city) ELSE city END,
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
    VALUES (v_mess.id, v_uid, 'customer_discovery_registration_submitted', 'registration_requests', v_req_id,
            jsonb_build_object('requested_name', trim(p_full_name), 'phone', trim(p_phone)));

    RETURN jsonb_build_object('request_id', v_req_id, 'mess_name', v_mess.name, 'status', 'pending');
END;
$$;

REVOKE ALL ON FUNCTION public.submit_customer_registration_by_mess(UUID, TEXT, TEXT, TEXT, TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.submit_customer_registration_by_mess(UUID, TEXT, TEXT, TEXT, TEXT, TEXT) TO authenticated;
