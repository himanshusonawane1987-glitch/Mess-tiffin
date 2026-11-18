# Tiffin Mess — Supabase Backend Setup Guide

## 1. Create Supabase Project & Apply Migrations
1. Create a new Supabase project in the Supabase Dashboard.
2. Open **SQL Editor** and run in order:
   - `supabase/migrations/001_initial_schema.sql`
   - `supabase/migrations/002_rls_and_functions.sql`

## 2. Configure Google OAuth & Android Redirect URL
1. In Google Cloud Console, create an OAuth 2.0 Web Client ID (and Android Client ID for package `com.aistudio.tiffinmess.kxpqzm` with your debug/release SHA-1).
2. In **Supabase Dashboard -> Authentication -> Providers -> Google**:
   - Enable Google provider.
   - Paste your Google Client ID and Client Secret.
3. In **Supabase Dashboard -> Authentication -> URL Configuration**:
   - Add `tiffinmess://auth/callback` to **Redirect URLs**.

## 3. Configure AI Studio Secrets
In AI Studio's **Secrets** panel, set:
- `SUPABASE_URL` = `https://<your-project-ref>.supabase.co`
- `SUPABASE_ANON_KEY` = `<your-supabase-publishable-or-anon-key>`
- `GOOGLE_WEB_CLIENT_ID` = `<optional-google-web-client-id-for-credential-manager>`

> Never put `SUPABASE_SERVICE_ROLE_KEY` in `.env` or Android client code.

## 4. Provision the First Mess Owner
1. Have the mess owner sign in once with Google inside the **Tiffin Mess** app (so their `auth.users` & `profiles` row exists).
2. In **Supabase Dashboard -> SQL Editor** (which runs as `postgres` / `service_role`), run:
```sql
SELECT public.provision_initial_owner(
    p_user_email := 'owner@example.com',
    p_mess_name := 'Annapurna Tiffin Mess',
    p_registration_code := 'ANNAPURNA2026',
    p_phone := '+91 9876543210',
    p_address := 'Near City Square, Pune',
    p_timezone := 'Asia/Kolkata'
);
```
3. Or deploy `supabase/functions/provision-owner/index.ts` and invoke it with `x-provision-secret`.
4. Re-open or refresh the app — the owner is automatically routed to the Admin Dashboard!

## 5. Customer Registration Flow
1. New customer signs in with Google.
2. Enters the mess registration code (`ANNAPURNA2026`), full name, mobile number, and optional room/address.
3. `submit_customer_registration` validates the bcrypt hash server-side and creates a `pending` request.
4. The mess owner/admin approves the request in **Customers -> Pending Requests**, activating customer access.
