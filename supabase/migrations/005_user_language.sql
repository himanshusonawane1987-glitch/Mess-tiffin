-- Tiffin Mess — Migration 005: Persistent User Language Preference (English / Hindi / Hinglish)
-- Apply after 004_customer_mark_and_leave.sql.

-- 1. Add language column on profiles (values: 'en' | 'hi' | 'hinglish', default: 'hinglish')
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS language TEXT NOT NULL DEFAULT 'hinglish',
    ADD COLUMN IF NOT EXISTS language_set_by_user BOOLEAN NOT NULL DEFAULT false;

DO $$
BEGIN
    ALTER TABLE public.profiles
        ADD CONSTRAINT chk_profiles_language
        CHECK (language IN ('en', 'hi', 'hinglish'));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- 2. Trusted RPC so signed-in users can update their saved language preference cleanly
CREATE OR REPLACE FUNCTION public.set_my_language(p_language TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_lang TEXT := lower(trim(coalesce(p_language, '')));
    v_profile public.profiles%ROWTYPE;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Not authenticated';
    END IF;

    IF v_lang NOT IN ('en', 'hi', 'hinglish') THEN
        RAISE EXCEPTION 'Invalid language. Must be en, hi, or hinglish.';
    END IF;

    UPDATE public.profiles
    SET language = v_lang,
        language_set_by_user = true
    WHERE id = v_uid
    RETURNING * INTO v_profile;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Profile not found';
    END IF;

    RETURN to_jsonb(v_profile);
END;
$$;

REVOKE ALL ON FUNCTION public.set_my_language(TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_my_language(TEXT) TO authenticated;
