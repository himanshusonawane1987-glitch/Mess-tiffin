-- Tiffin Mess — Migration 006: Seller UPI Settings, Private Storage & Customer Payment Screenshot Proofs
-- Apply after 005_user_language.sql. Do NOT edit migrations 001..005.

-- 1. Add UPI ID and private QR storage path columns to messes
ALTER TABLE public.messes
    ADD COLUMN IF NOT EXISTS upi_id TEXT,
    ADD COLUMN IF NOT EXISTS upi_qr_path TEXT;

-- Never expose seller UPI ID or QR path to public discovery queries or non-members
REVOKE SELECT (upi_id, upi_qr_path) ON public.messes FROM anon, authenticated;

-- 2. Payment Proofs table (proof is NEVER an auto-paid payment until seller confirms)
CREATE TABLE IF NOT EXISTS public.payment_proofs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mess_id UUID NOT NULL REFERENCES public.messes(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES public.customer_profiles(id) ON DELETE CASCADE,
    bill_id UUID REFERENCES public.monthly_bills(id) ON DELETE SET NULL,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    screenshot_path TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'confirmed', 'rejected')),
    reject_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES public.profiles(id)
);

CREATE INDEX IF NOT EXISTS idx_payment_proofs_mess_status
    ON public.payment_proofs (mess_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payment_proofs_customer
    ON public.payment_proofs (customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payment_proofs_bill
    ON public.payment_proofs (bill_id, status);

ALTER TABLE public.payment_proofs ENABLE ROW LEVEL SECURITY;

-- Customers read only their own proofs; mess admins/owners read proofs of their mess
DO $$
BEGIN
    CREATE POLICY "Customers read own payment proofs"
        ON public.payment_proofs
        FOR SELECT
        TO authenticated
        USING (public.is_own_customer_profile(customer_id));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE POLICY "Mess admins read mess payment proofs"
        ON public.payment_proofs
        FOR SELECT
        TO authenticated
        USING (public.is_mess_admin(mess_id));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- 3. Private Supabase Storage bucket `payment-proofs` & strict object policies
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'payment-proofs',
    'payment-proofs',
    false,
    5242880,
    ARRAY['image/jpeg', 'image/png', 'image/webp']
)
ON CONFLICT (id) DO UPDATE SET
    public = false,
    file_size_limit = 5242880,
    allowed_mime_types = ARRAY['image/jpeg', 'image/png', 'image/webp'];

-- Helper to safely extract UUID from folder path segments without raising cast errors
CREATE OR REPLACE FUNCTION public.try_parse_uuid(p_text TEXT)
RETURNS UUID
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT CASE
        WHEN p_text ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        THEN p_text::uuid
        ELSE NULL
    END;
$$;

-- Seller QR paths: "{mess_id}/qr/{filename}"
DO $$
BEGIN
    CREATE POLICY "Mess admins manage QR images"
        ON storage.objects
        FOR ALL
        TO authenticated
        USING (
            bucket_id = 'payment-proofs'
            AND (storage.foldername(name))[2] = 'qr'
            AND public.is_mess_admin(public.try_parse_uuid((storage.foldername(name))[1]))
        )
        WITH CHECK (
            bucket_id = 'payment-proofs'
            AND (storage.foldername(name))[2] = 'qr'
            AND public.is_mess_admin(public.try_parse_uuid((storage.foldername(name))[1]))
        );
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- QR readable ONLY by active members (customers/admins/owners) of that mess
DO $$
BEGIN
    CREATE POLICY "Active mess members read seller QR images"
        ON storage.objects
        FOR SELECT
        TO authenticated
        USING (
            bucket_id = 'payment-proofs'
            AND (storage.foldername(name))[2] = 'qr'
            AND (
                public.is_mess_active_member(public.try_parse_uuid((storage.foldername(name))[1]))
                OR public.is_mess_admin(public.try_parse_uuid((storage.foldername(name))[1]))
            )
        );
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- Customer proof paths: "{mess_id}/proofs/{customer_id}/{filename}"
DO $$
BEGIN
    CREATE POLICY "Active customers upload own payment proof screenshots"
        ON storage.objects
        FOR INSERT
        TO authenticated
        WITH CHECK (
            bucket_id = 'payment-proofs'
            AND (storage.foldername(name))[2] = 'proofs'
            AND public.is_mess_active_member(public.try_parse_uuid((storage.foldername(name))[1]))
            AND public.is_own_customer_profile(public.try_parse_uuid((storage.foldername(name))[3]))
            AND EXISTS (
                SELECT 1
                FROM public.customer_profiles cp
                WHERE cp.id = public.try_parse_uuid((storage.foldername(name))[3])
                  AND cp.mess_id = public.try_parse_uuid((storage.foldername(name))[1])
            )
        );
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- Proof screenshots readable ONLY by the submitting customer or mess admins/owners
DO $$
BEGIN
    CREATE POLICY "Customers and mess admins read payment proof screenshots"
        ON storage.objects
        FOR SELECT
        TO authenticated
        USING (
            bucket_id = 'payment-proofs'
            AND (storage.foldername(name))[2] = 'proofs'
            AND (
                (
                    public.is_own_customer_profile(public.try_parse_uuid((storage.foldername(name))[3]))
                    AND public.is_mess_active_member(public.try_parse_uuid((storage.foldername(name))[1]))
                )
                OR public.is_mess_admin(public.try_parse_uuid((storage.foldername(name))[1]))
            )
        );
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- 4. Trusted RPCs for Seller UPI Settings & Active-Member UPI Fetch

CREATE OR REPLACE FUNCTION public.seller_update_upi_settings(
    p_mess_id UUID,
    p_upi_id TEXT,
    p_upi_qr_path TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_upi TEXT := NULLIF(trim(coalesce(p_upi_id, '')), '');
    v_qr TEXT := NULLIF(trim(coalesce(p_upi_qr_path, '')), '');
    v_mess public.messes%ROWTYPE;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;
    IF NOT public.is_mess_admin(p_mess_id) THEN
        RAISE EXCEPTION 'Unauthorized: mess admin access required';
    END IF;
    IF v_upi IS NOT NULL AND v_upi !~ '^[a-zA-Z0-9._-]{2,}@[a-zA-Z0-9]{2,}$' THEN
        RAISE EXCEPTION 'Invalid UPI ID format (expected e.g. name@okhdfcbank)';
    END IF;
    IF v_qr IS NOT NULL AND v_qr NOT LIKE (p_mess_id::text || '/qr/%') THEN
        RAISE EXCEPTION 'Invalid QR storage path for this mess';
    END IF;

    UPDATE public.messes
    SET upi_id = v_upi,
        upi_qr_path = COALESCE(v_qr, public.messes.upi_qr_path)
    WHERE id = p_mess_id
    RETURNING * INTO v_mess;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Mess not found';
    END IF;

    INSERT INTO public.audit_logs (mess_id, actor_id, action, entity_type, entity_id, new_values)
    VALUES (
        p_mess_id,
        v_uid,
        'upi_settings_updated',
        'messes',
        p_mess_id,
        jsonb_build_object('upi_id', v_mess.upi_id, 'has_qr', v_mess.upi_qr_path IS NOT NULL)
    );

    RETURN jsonb_build_object(
        'upi_id', v_mess.upi_id,
        'upi_qr_path', v_mess.upi_qr_path
    );
END;
$$;

REVOKE ALL ON FUNCTION public.seller_update_upi_settings(UUID, TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.seller_update_upi_settings(UUID, TEXT, TEXT) TO authenticated;

CREATE OR REPLACE FUNCTION public.get_my_mess_upi_details(p_mess_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_upi_id TEXT;
    v_upi_qr_path TEXT;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;
    IF NOT (public.is_mess_active_member(p_mess_id) OR public.is_mess_admin(p_mess_id)) THEN
        RAISE EXCEPTION 'Unauthorized: active mess membership required';
    END IF;

    SELECT m.upi_id, m.upi_qr_path
    INTO v_upi_id, v_upi_qr_path
    FROM public.messes m
    WHERE m.id = p_mess_id;

    RETURN jsonb_build_object(
        'upi_id', v_upi_id,
        'upi_qr_path', v_upi_qr_path
    );
END;
$$;

REVOKE ALL ON FUNCTION public.get_my_mess_upi_details(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_my_mess_upi_details(UUID) TO authenticated;

-- 5. Submit Payment Proof RPC (authenticated only, active customer's own bill/mess only)
CREATE OR REPLACE FUNCTION public.submit_payment_proof(
    p_bill_id UUID,
    p_amount NUMERIC,
    p_screenshot_path TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_cust public.customer_profiles%ROWTYPE;
    v_bill public.monthly_bills%ROWTYPE;
    v_path TEXT := trim(coalesce(p_screenshot_path, ''));
    v_amount NUMERIC(12, 2);
    v_proof public.payment_proofs%ROWTYPE;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;
    IF v_path = '' THEN
        RAISE EXCEPTION 'Payment screenshot is required';
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

    IF v_path NOT LIKE (v_cust.mess_id::text || '/proofs/' || v_cust.id::text || '/%') THEN
        RAISE EXCEPTION 'Unauthorized screenshot storage path';
    END IF;

    IF p_bill_id IS NOT NULL THEN
        SELECT * INTO v_bill FROM public.monthly_bills WHERE id = p_bill_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Bill not found';
        END IF;
        IF v_bill.customer_id <> v_cust.id OR v_bill.mess_id <> v_cust.mess_id THEN
            RAISE EXCEPTION 'Unauthorized: bill does not belong to your active mess';
        END IF;
        IF v_bill.balance_due <= 0 THEN
            RAISE EXCEPTION 'This bill is already paid';
        END IF;
        v_amount := round(coalesce(p_amount, v_bill.balance_due), 2);
        IF v_amount <= 0 OR v_amount > v_bill.balance_due THEN
            RAISE EXCEPTION 'Amount must be greater than zero and at most remaining due (%).', v_bill.balance_due;
        END IF;
    ELSE
        IF p_amount IS NULL OR p_amount <= 0 THEN
            RAISE EXCEPTION 'Amount must be greater than zero';
        END IF;
        v_amount := round(p_amount, 2);
    END IF;

    -- Insert proof as PENDING; NEVER marks the bill as paid automatically
    INSERT INTO public.payment_proofs (
        mess_id, customer_id, bill_id, amount, screenshot_path, status
    )
    VALUES (
        v_cust.mess_id, v_cust.id, p_bill_id, v_amount, v_path, 'pending'
    )
    RETURNING * INTO v_proof;

    INSERT INTO public.audit_logs (mess_id, actor_id, action, entity_type, entity_id, new_values)
    VALUES (
        v_cust.mess_id,
        v_uid,
        'payment_proof_submitted',
        'payment_proofs',
        v_proof.id,
        jsonb_build_object('bill_id', p_bill_id, 'amount', v_amount)
    );

    RETURN to_jsonb(v_proof);
END;
$$;

REVOKE ALL ON FUNCTION public.submit_payment_proof(UUID, NUMERIC, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.submit_payment_proof(UUID, NUMERIC, TEXT) TO authenticated;

-- 6. Review Payment Proof RPC (mess admin/owner only; confirm calls existing record_customer_payment)
CREATE OR REPLACE FUNCTION public.review_payment_proof(
    p_proof_id UUID,
    p_decision TEXT,
    p_reject_reason TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_decision TEXT := lower(trim(coalesce(p_decision, '')));
    v_proof public.payment_proofs%ROWTYPE;
    v_target_bill_id UUID;
    v_reason TEXT := NULLIF(trim(coalesce(p_reject_reason, '')), '');
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;
    IF v_decision NOT IN ('confirmed', 'rejected') THEN
        RAISE EXCEPTION 'Decision must be confirmed or rejected';
    END IF;

    SELECT * INTO v_proof FROM public.payment_proofs WHERE id = p_proof_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Payment proof not found';
    END IF;
    IF NOT public.is_mess_admin(v_proof.mess_id) THEN
        RAISE EXCEPTION 'Unauthorized: mess admin access required';
    END IF;
    IF v_proof.status <> 'pending' THEN
        RAISE EXCEPTION 'Payment proof has already been reviewed';
    END IF;

    IF v_decision = 'confirmed' THEN
        v_target_bill_id := v_proof.bill_id;
        IF v_target_bill_id IS NULL THEN
            SELECT id INTO v_target_bill_id
            FROM public.monthly_bills
            WHERE customer_id = v_proof.customer_id
              AND mess_id = v_proof.mess_id
              AND balance_due >= v_proof.amount
            ORDER BY billing_month ASC
            LIMIT 1;
        END IF;
        IF v_target_bill_id IS NULL THEN
            RAISE EXCEPTION 'No open bill found for this payment proof';
        END IF;

        -- Record payment using the existing trusted ledger RPC (updates monthly_bills atomically)
        PERFORM public.record_customer_payment(
            v_target_bill_id,
            v_proof.amount,
            'upi',
            'UPI Proof #' || left(v_proof.id::text, 8),
            now()
        );

        UPDATE public.payment_proofs
        SET status = 'confirmed',
            reject_reason = NULL,
            reviewed_at = now(),
            reviewed_by = v_uid
        WHERE id = p_proof_id
        RETURNING * INTO v_proof;

        INSERT INTO public.audit_logs (mess_id, actor_id, action, entity_type, entity_id, new_values)
        VALUES (
            v_proof.mess_id,
            v_uid,
            'payment_proof_confirmed',
            'payment_proofs',
            v_proof.id,
            jsonb_build_object('bill_id', v_target_bill_id, 'amount', v_proof.amount)
        );
    ELSE
        -- Reject: bill remains untouched / unpaid
        UPDATE public.payment_proofs
        SET status = 'rejected',
            reject_reason = v_reason,
            reviewed_at = now(),
            reviewed_by = v_uid
        WHERE id = p_proof_id
        RETURNING * INTO v_proof;

        INSERT INTO public.audit_logs (mess_id, actor_id, action, entity_type, entity_id, new_values)
        VALUES (
            v_proof.mess_id,
            v_uid,
            'payment_proof_rejected',
            'payment_proofs',
            v_proof.id,
            jsonb_build_object('bill_id', v_proof.bill_id, 'reject_reason', v_reason)
        );
    END IF;

    RETURN to_jsonb(v_proof);
END;
$$;

REVOKE ALL ON FUNCTION public.review_payment_proof(UUID, TEXT, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.review_payment_proof(UUID, TEXT, TEXT) TO authenticated;

-- 7. Update get_my_auth_context() to expose upi_id & upi_qr_path ONLY when membership is active
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
    v_show_upi BOOLEAN := false;
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
        v_show_upi := (v_membership.membership_status = 'active' AND v_profile.account_status = 'active');

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
            'upi_id', CASE WHEN v_show_upi THEN m.upi_id ELSE NULL END,
            'upi_qr_path', CASE WHEN v_show_upi THEN m.upi_qr_path ELSE NULL END,
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

-- 8. Enable Supabase Realtime on payment_proofs and payments
DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.payment_proofs;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER PUBLICATION supabase_realtime ADD TABLE public.payments;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
