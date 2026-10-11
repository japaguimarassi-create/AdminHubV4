import { createClient } from "npm:@supabase/supabase-js@2.57.4";
import { createRemoteJWKSet, jwtVerify } from "npm:jose@6.1.0";

const allowedOrigin = Deno.env.get("JARVIS_ALLOWED_ORIGIN") ?? "*";
const corsHeaders = {
  "Access-Control-Allow-Origin": allowedOrigin,
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store"
};

const googleKeys = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));

function respond(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return respond(405, { ok: false, error: "Use POST." });

  const clientId = Deno.env.get("GOOGLE_WEB_CLIENT_ID") ?? "188208581214-dtshluh0fkclfq8mnupaf2nn35l1flrc.apps.googleusercontent.com";
  const projectUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!clientId || !projectUrl || !serviceKey) {
    return respond(503, { ok: false, error: "O backend de identidade não foi configurado." });
  }

  try {
    const body = await request.json();
    const idToken = typeof body.id_token === "string" ? body.id_token : "";
    const expectedNonce = typeof body.nonce === "string" ? body.nonce : "";
    if (!idToken || !expectedNonce) return respond(400, { ok: false, error: "Credencial incompleta." });

    const verified = await jwtVerify(idToken, googleKeys, {
      issuer: ["https://accounts.google.com", "accounts.google.com"],
      audience: clientId,
      algorithms: ["RS256"]
    });
    const claims = verified.payload;
    if (claims.nonce !== expectedNonce) {
      return respond(401, { ok: false, error: "A verificação de segurança do login falhou." });
    }
    if (claims.email_verified !== true || typeof claims.email !== "string" || typeof claims.sub !== "string") {
      return respond(401, { ok: false, error: "A Conta Google não forneceu uma identidade verificada." });
    }

    const supabase = createClient(projectUrl, serviceKey, {
      auth: { autoRefreshToken: false, persistSession: false }
    });
    const { error: profileError } = await supabase.from("jarvis_profiles").upsert({
      google_sub: claims.sub,
      email: claims.email.toLowerCase(),
      display_name: typeof claims.name === "string" ? claims.name : "",
      avatar_url: typeof claims.picture === "string" ? claims.picture : "",
      last_seen_at: new Date().toISOString()
    }, { onConflict: "google_sub" });
    if (profileError) {
      return respond(500, { ok: false, error: "Não consegui salvar o perfil seguro." });
    }

    const { data: isOwner, error: ownerError } = await supabase.rpc("claim_jarvis_owner", {
      p_google_sub: claims.sub
    });
    if (ownerError) return respond(500, { ok: false, error: "Não consegui verificar o proprietário global." });

    return respond(200, {
      ok: true,
      email: claims.email,
      name: typeof claims.name === "string" ? claims.name : "",
      picture: typeof claims.picture === "string" ? claims.picture : "",
      is_owner: isOwner === true
    });
  } catch (_error) {
    return respond(401, { ok: false, error: "O token Google é inválido, expirou ou não corresponde ao pedido." });
  }
});
