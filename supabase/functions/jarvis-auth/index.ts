import { createClient } from "npm:@supabase/supabase-js@2.57.4";
import { createRemoteJWKSet, jwtVerify } from "npm:jose@6.1.0";

const corsHeaders = {
  "Access-Control-Allow-Origin": Deno.env.get("JARVIS_ALLOWED_ORIGIN") ?? "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store"
};

const NONCE_TTL_MS = 10 * 60 * 1000;
const MAX_PENDING_NONCES = 500;

const googleKeys = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));

function respond(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}

function newNonce(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return respond(405, { ok: false, error: "Use POST." });

  const clientId = "188208581214-dtshluh0fkclfq8mnupaf2nn35l1flrc.apps.googleusercontent.com";
  const projectUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!clientId || !projectUrl || !serviceKey) {
    return respond(503, { ok: false, error: "O backend de identidade não foi configurado." });
  }

  const supabase = createClient(projectUrl, serviceKey, {
    auth: { autoRefreshToken: false, persistSession: false }
  });
  const cutoff = () => new Date(Date.now() - NONCE_TTL_MS).toISOString();

  try {
    const body = await request.json();

    // Passo 1: o cliente pede um nonce emitido pelo servidor.
    if (body.action === "nonce") {
      await supabase.from("jarvis_nonces").delete().lt("created_at", cutoff());
      const { count, error: countError } = await supabase
        .from("jarvis_nonces")
        .select("nonce", { count: "exact", head: true })
        .is("used_at", null);
      if (countError) return respond(500, { ok: false, error: "Não consegui preparar o login seguro." });
      if ((count ?? 0) >= MAX_PENDING_NONCES) {
        return respond(429, { ok: false, error: "Muitas tentativas pendentes. Tente novamente em alguns minutos." });
      }
      const nonce = newNonce();
      const { error: insertError } = await supabase.from("jarvis_nonces").insert({ nonce });
      if (insertError) return respond(500, { ok: false, error: "Não consegui preparar o login seguro." });
      return respond(200, { ok: true, nonce, expires_in: NONCE_TTL_MS / 1000 });
    }

    // Passo 2: login com o id_token que carrega esse nonce.
    const idToken = typeof body.id_token === "string" ? body.id_token : "";
    if (!idToken) return respond(400, { ok: false, error: "Credencial incompleta." });

    const verified = await jwtVerify(idToken, googleKeys, {
      issuer: ["https://accounts.google.com", "accounts.google.com"],
      audience: clientId,
      algorithms: ["RS256"]
    });
    const claims = verified.payload;
    if (claims.email_verified !== true || typeof claims.email !== "string" || typeof claims.sub !== "string") {
      return respond(401, { ok: false, error: "A Conta Google não forneceu uma identidade verificada." });
    }

    const tokenNonce = typeof claims.nonce === "string" ? claims.nonce : "";
    if (!tokenNonce) {
      return respond(401, { ok: false, error: "A verificação de segurança do login falhou." });
    }

    // Modo legado (desligado por padrão): aceita nonce gerado pelo cliente, SEM proteção contra replay.
    const legacyOk = Deno.env.get("JARVIS_ALLOW_CLIENT_NONCE") === "true" &&
      typeof body.nonce === "string" && body.nonce === tokenNonce;

    if (!legacyOk) {
      // Consome o nonce de forma atômica: um único UPDATE, só vale uma vez e dentro do prazo.
      const { data: consumed, error: nonceError } = await supabase
        .from("jarvis_nonces")
        .update({ used_at: new Date().toISOString() })
        .eq("nonce", tokenNonce)
        .is("used_at", null)
        .gte("created_at", cutoff())
        .select("nonce");
      if (nonceError) return respond(500, { ok: false, error: "Não consegui validar o login." });
      if (!consumed || consumed.length !== 1) {
        return respond(401, { ok: false, error: "A verificação de segurança do login falhou ou expirou." });
      }
    }

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
