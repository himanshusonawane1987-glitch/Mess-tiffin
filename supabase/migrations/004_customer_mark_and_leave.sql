-- Tiffin Mess — Migration 004: Customer Daily Tiffin Mark, No-Tiffin Leave, Cutoff Time & Billing Exclusion
-- Apply after 001_initial_schema.sql, 002_rls_and_functions.sql, and 003_seller_registration_and_discovery.sql.

-- 1. Add seller-configurable daily tiffin marking cutoff time on messes (default 10:00 in mess timezone)
ALTER TABLE public.messes
    ADD COLUMN IF NOT EXISTS daily_mark_cutoff_time TEXT NOT NULL DEFAULT '10:00';

DO $$
BEGIN
    ALTER TABLE public.messes
        ADD CONSTRAINT chk_messes_daily_cutoff_time
        CHECK (daily_mark_cutoff_time ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- 2. Customer daily tiffin marks ("Tiffin Chahiye" = wanted / "Nahi Chahiye" = not_wanted)
CREATE TABLE IF NOT EXISTS public.customer_daily_marks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mess_id UUID NOT NULL REFERENCES public.messes(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES public.customer_profiles(id) ON DELETE CASCADE,
    service_date DATE NOT NULL,
    choice TEXT NOT NULL CHECK (choice IN ('wanted', 'not_wanted')),
    marked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (mess_id, customer_id, service_date)
);

CREATE INDEX IF NOT EXISTS idx_customer_daily_marks_mess_date
    ON public.customer_daily_marks (mess_id, service_date);

DROP TRIGGER IF EXISTS trg_customer_daily_marks_updated_at ON public.customer_daily_marks;
CREATE TRIGGER trg_customer_daily_marks_updated_at
    BEFORE UPDATE ON public.customer_daily_marks
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

ALTER TABLE public.customer_daily_marks ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    CREATE POLICY "Customers read own daily marks"
        ON public.customer_daily_marks
        FOR SELECT
        TO authenticated
        USING (public.is_own_customer_profile(customer_id));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE POLICY "Admins read mess daily marks"
        ON public.customer_daily_marks
        FOR SELECT
        TO authenticated
        USING (public.is_mess_admin(mess_id));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- 3. "No Tiffin" Leave table + RLS (Feature F)
CREATE TABLE IF NOT EXISTS public.tiffin_leaves (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mess_id UUID NOT NULL REFERENCES public.messes(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES public.customer_profiles(id) ON DELETE CASCADE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    reason TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'cancelled')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (end_date >= start_date)
);

CREATE INDEX IF NOT EXISTS idx_tiffin_leaves_mess_dates
    ON public.tiffin_leaves (mess_id, status, start_date, end_date);
CREATE INDEX IF NOT EXISTS idx_tiffin_leaves_customer
    ON public.tiffin_leaves (customer_id, start_date DESC);

ALTER TABLE public.tiffin_leaves ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    CREATE POLICY "Customers read own leaves"
        ON public.tiffin_leaves
        FOR SELECT
        TO authenticated
        USING (public.is_own_customer_profile(customer_id));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE POLICY "Customers insert own leaves"
        ON public.tiffin_leaves
        FOR INSERT
        TO authenticated
        WITH CHECK (
            public.is_own_customer_profile(customer_id)
            AND EXISTS (
                SELECT 1 FROM public.customer_profiles cp
                WHERE cp.id = customer_id AND cp.mess_id = tiffin_leaves.mess_id
            )
        );
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE POLICY "Customers cancel own upcoming leaves before start date"
        ON public.tiffin_leaves
        FOR UPDATE
        TO authenticated
        USING (
            public.is_own_customer_profile(customer_id)
            AND status = 'active'
            AND start_date > (now() AT TIME ZONE (SELECT m.timezone FROM public.messes m WHERE m.id = tiffin_leaves.mess_id))::date
        )
        WITH CHECK (
            public.is_own_customer_profile(customer_id)
            AND status = 'cancelled'
        );
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE POLICY "Mess admins manage leaves of their mess"
        ON public.tiffin_leaves
        FOR ALL
        TO authenticated
        USING (public.is_mess_admin(mess_id))
        WITH CHECK (public.is_mess_admin(mess_id));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- 4. Secure RPC for customer to mark today's tiffin before cutoff time (Feature E)
CREATE OR REPLACE FUNCTION public.customer_mark_today_tiffin(
    p_choice TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_choice TEXT := lower(trim(coalesce(p_choice, '')));
    v_cust public.customer_profiles%ROWTYPE;
    v_mess public.messes%ROWTYPE;
    v_now_local TIMESTAMP;
    v_today DATE;
    v_local_time TIME;
    v_cutoff TIME;
    v_on_leave BOOLEAN := false;
    v_mark public.customer_daily_marks%ROWTYPE;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;
    IF v_choice NOT IN ('wanted', 'not_wanted') THEN
        RAISE EXCEPTION 'Invalid choice. Must be wanted or not_wanted.';
    END IF;

    SELECT cp.* INTO v_cust
    FROM public.customer_profiles cp
    JOIN public.mess_members mm ON mm.mess_id = cp.mess_id AND mm.user_id = v_uid
    JOIN public.profiles p ON p.id = v_uid
    WHERE cp.user_id = v_uid
      AND cp.customer_status = 'active'
      AND mm.membership_status = 'active'
      AND p.account_status = 'active'
    ORDER BY mm.joined_at DESC NULLS LAST
    LIMIT 1;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'No active customer membership found';
    END IF;

    SELECT * INTO v_mess FROM public.messes WHERE id = v_cust.mess_id;
    v_now_local := (now() AT TIME ZONE v_mess.timezone);
    v_today := v_now_local::date;
    v_local_time := v_now_local::time;

    BEGIN
        v_cutoff := v_mess.daily_mark_cutoff_time::time;
    EXCEPTION WHEN OTHERS THEN
        v_cutoff := '10:00'::time;
    END;

    IF v_local_time > v_cutoff THEN
        RAISE EXCEPTION 'Cutoff time (%) has passed for today.', v_mess.daily_mark_cutoff_time;
    END IF;

    SELECT EXISTS (
        SELECT 1
        FROM public.tiffin_leaves tl
        WHERE tl.customer_id = v_cust.id
          AND tl.status = 'active'
          AND v_today BETWEEN tl.start_date AND tl.end_date
    ) INTO v_on_leave;

    IF v_on_leave AND v_choice = 'wanted' THEN
        RAISE EXCEPTION 'You are on No-Tiffin Leave today. Cancel your leave to request a tiffin.';
    END IF;

    INSERT INTO public.customer_daily_marks (mess_id, customer_id, service_date, choice, marked_at)
    VALUES (v_cust.mess_id, v_cust.id, v_today, v_choice, now())
    ON CONFLICT (mess_id, customer_id, service_date) DO UPDATE SET
        choice = EXCLUDED.choice,
        marked_at = now()
    RETURNING * INTO v_mark;

    INSERT INTO public.audit_logs (mess_id, actor_id, action, entity_type, entity_id, new_values)
    VALUES (
        v_cust.mess_id,
        v_uid,
        'customer_daily_marked',
        'customer_daily_marks',
        v_mark.id,
        jsonb_build_object('service_date', v_today, 'choice', v_choice)
    );

    RETURN to_jsonb(v_mark);
END;
$$;

REVOKE ALL ON FUNCTION public.customer_mark_today_tiffin(TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.customer_mark_today_tiffin(TEXT) TO authenticated;

-- 5. Update bulk_mark_tiffin_delivered so customers on leave or marked "not_wanted" are never marked delivered in bulk
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
        IF EXISTS (
            SELECT 1 FROM public.customer_profiles cp
            WHERE cp.id = v_cid
              AND cp.mess_id = p_mess_id
              AND cp.customer_status = 'active'
              AND NOT EXISTS (
                  SELECT 1 FROM public.customer_daily_marks cdm
                  WHERE cdm.customer_id = v_cid
                    AND cdm.service_date = p_service_date
                    AND cdm.choice = 'not_wanted'
              )
              AND NOT EXISTS (
                  SELECT 1 FROM public.tiffin_leaves tl
                  WHERE tl.customer_id = v_cid
                    AND tl.status = 'active'
                    AND p_service_date BETWEEN tl.start_date AND tl.end_date
              )
        ) THEN
            PERFORM public.upsert_tiffin_record(v_cid, p_service_date, p_meal_slot, 'delivered', p_note);
            v_count := v_count + 1;
        END IF;
    END LOOP;

    RETURN v_count;
END;
$$;

-- 6. Update generate_monthly_bill to strictly exclude "Nahi Chahiye" days and active leave days
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

    -- Count delivered tiffins, excluding any date marked not_wanted ("Nahi Chahiye") or covered by active leave
    SELECT count(*)::integer INTO v_delivered_count
    FROM public.tiffin_records tr
    WHERE tr.customer_id = p_customer_id
      AND tr.status = 'delivered'
      AND tr.service_date BETWEEN v_month_start AND v_month_end
      AND NOT EXISTS (
          SELECT 1 FROM public.customer_daily_marks cdm
          WHERE cdm.customer_id = tr.customer_id
            AND cdm.service_date = tr.service_date
            AND cdm.choice = 'not_wanted'
      )
      AND NOT EXISTS (
          SELECT 1 FROM public.tiffin_leaves tl
          WHERE tl.customer_id = tr.customer_id
            AND tl.status = 'active'
            AND tr.service_date BETWEEN tl.start_date AND tl.end_date
      );

    IF v_sub.id IS NOT NULL THEN
        v_sub_charge := v_sub.agreed_price;
        IF v_sub.included_tiffins > 0 AND v_delivered_count > v_sub.included_tiffins THEN
            v_extra_count := v_delivered_count - v_sub.included_tiffins;
            v_extra_charge := round(v_extra_count * v_sub.extra_tiffin_price_snapshot, 2);
        ELSIF v_sub.agreed_price = 0 AND v_sub.extra_tiffin_price_snapshot > 0 THEN
            v_extra_count := v_delivered_count;
            v_extra_charge := round(v_delivered_count * v_sub.extra_tiffin_price_snapshot, 2);
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

-- 7. Update get_my_auth_context() to include daily_mark_cutoff_time in mess JSONB
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
            'daily_mark_cutoff_time', m.daily_mark_cutoff_time,
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

-- 8. Enable Realtime on daily marks and leaves
DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.customer_daily_marks;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.tiffin_leaves;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
