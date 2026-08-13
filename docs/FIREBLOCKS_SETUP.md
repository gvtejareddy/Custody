# Fireblocks Console Setup (quick checklist)

1. Login to the Fireblocks Console with your org admin account.
2. Create a new API Key for the custody application. When creating, select appropriate permissions (wallets/transactions/webhooks) and generate a private key. Download and store the private key securely.
3. Note the API Key ID; you'll use it as `FIREBLOCKS_API_KEY_ID`.
4. Create Vault Accounts and Vault Names representing bank-owned custody buckets.
5. Configure allowed assets and networks for the Vaults (BTC, ETH, USDC for MVP).
6. Configure the Webhook endpoint and ensure your app endpoint is reachable from Fireblocks. You may use ngrok for local testing.
7. (Optional) Create an IP allowlist in Fireblocks and add your production outbound IPs.

Security notes

- The private key is sensitive — treat it like any other signing key. Use Key Vaults for storage in production.
- Fireblocks also supports API key rotation — plan for rotation automation.
