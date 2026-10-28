import { cloudflareTest, readD1Migrations } from "@cloudflare/vitest-plugin";
import { exportJWK, generateKeyPair } from "jose";
import { defineConfig } from "vitest/config";

/**
 * Test harness config. We generate a throwaway RS256 keypair here (Node side),
 * expose the PUBLIC JWKS to the Worker as CLERK_JWKS_JSON (so the real jose
 * signature check runs against it), and hand the PRIVATE JWK to the tests via
 * TEST_PRIVATE_JWK so they can mint valid Clerk-shaped tokens. Nothing here is a
 * real credential — keys exist only for the duration of the test run.
 */
export default defineConfig(async () => {
  // Resolved against the package dir (cwd when running the npm scripts).
  const migrations = await readD1Migrations("migrations");

  const kid = "niyyah-test-key";
  const { publicKey, privateKey } = await generateKeyPair("RS256", { extractable: true });
  const pubJwk = { ...(await exportJWK(publicKey)), kid, alg: "RS256", use: "sig" };
  const privJwk = { ...(await exportJWK(privateKey)), kid, alg: "RS256" };

  const TEST_ISSUER = "https://test.niyyah.local";

  return {
    plugins: [
      cloudflareTest({
        wrangler: { configPath: "./wrangler.jsonc" },
        miniflare: {
          bindings: {
            TEST_MIGRATIONS: migrations,
            TEST_PRIVATE_JWK: JSON.stringify(privJwk),
            CLERK_ISSUER: TEST_ISSUER,
            CLERK_AUTHORIZED_PARTIES: "",
            CLERK_JWKS_JSON: JSON.stringify({ keys: [pubJwk] }),
          },
        },
      }),
    ],
  };
});
