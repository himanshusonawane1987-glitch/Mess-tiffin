// Supabase Edge Function: provision-owner
// Deploy with: supabase functions deploy provision-owner
// Requires Authorization: Bearer <SUPABASE_SERVICE_ROLE_KEY> or PROVISION_SECRET header.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: { "Content-Type": "application/json" },
    });
  }

  const provisionSecret = Deno.env.get("PROVISION_SECRET");
  const authHeader = req.headers.get("x-provision-secret");
  if (!provisionSecret || authHeader !== provisionSecret) {
    return new Response(JSON.stringify({ error: "Unauthorized" }), {
      status: 401,
      headers: { "Content-Type": "application/json" },
    });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const adminClient = createClient(supabaseUrl, serviceRoleKey);

  const body = await req.json();
  const { userEmail, messName, registrationCode, phone = "", address = "", timezone = "Asia/Kolkata" } = body;

  const { data, error } = await adminClient.rpc("provision_initial_owner", {
    p_user_email: userEmail,
    p_mess_name: messName,
    p_registration_code: registrationCode,
    p_phone: phone,
    p_address: address,
    p_timezone: timezone,
  });

  if (error) {
    return new Response(JSON.stringify({ error: error.message }), {
      status: 400,
      headers: { "Content-Type": "application/json" },
    });
  }

  return new Response(JSON.stringify(data), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
});
