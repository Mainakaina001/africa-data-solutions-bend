# Deployment

CI/CD: on every push to `main`, GitHub Actions runs the test suite against a
throwaway Postgres, builds a Docker image, pushes it to GHCR
(`ghcr.io/<owner>/africa-data-solutions`), then SSHes into the deploy server
and redeploys via `docker compose`. Workflow: `.github/workflows/deploy.yml`.

## One-time setup

### 1. InterServer VPS

Running on InterServer VPS `vps3583260` (KVM, Ubuntu 26.04, public IP
`162.35.113.12`).

- 1.6 GB RAM, 1 GB swap already provisioned by InterServer — enough headroom
  for Postgres + the JVM + Caddy together. The Dockerfile sizes the JVM heap
  as a percentage of available memory rather than a fixed value.
- A dedicated `deploy` user (not root) was created for CI/CD, with
  passwordless `sudo` and membership in the `docker` group.
- `ufw` is enabled with `22/tcp`, `80/tcp`, `443/tcp` open — no separate
  cloud-level security group to worry about here (unlike Oracle Cloud's
  VCN + iptables double firewall).
- Docker + the Compose plugin are installed via `get.docker.com`.
- `africadatasolutions.org` already hosts the frontend, so don't repoint the
  root domain — in Hostinger's DNS zone editor, add a new **A record**: name
  `api`, value `162.35.113.12`. This gives the backend
  `api.africadatasolutions.org` without touching the existing frontend
  records. Caddy needs this DNS record live before it can issue a Let's
  Encrypt certificate.

### 2. Deploy SSH key

A dedicated ed25519 key pair (not a personal key) was generated for GitHub
Actions and its public half added to `/home/deploy/.ssh/authorized_keys` on
the VPS. Keep the private half for the GitHub secret below — it isn't stored
in this repo.

### 3. GitHub repository secrets

Settings → Secrets and variables → Actions → New repository secret:

| Secret | Value |
|---|---|
| `DEPLOY_HOST` | `162.35.113.12` |
| `DEPLOY_USER` | `deploy` |
| `DEPLOY_SSH_KEY` | Base64 of the private deploy key (`base64 -w0 deploy_key`) |
| `DEPLOY_SSH_PORT` | Only if not 22 |

`GITHUB_TOKEN` (used for GHCR push/pull) is automatic — no secret to add.

### 4. Server-side `.env`

On the server:

```bash
sudo mkdir -p /opt/africa-data-solutions
cd /opt/africa-data-solutions
```

Copy `.env.example` from the repo, rename to `.env`, and fill in real values
— **this file is never committed and never copied by CI**, so it's the only
place production secrets live. At minimum you must set: `DATABASE_PASSWORD`,
`JWT_SECRET` (generate with `openssl rand -base64 64`), `APP_URL`,
`FRONTEND_ORIGINS`, `DOMAIN`, `GHCR_OWNER` (your GitHub username/org),
`GHCR_IMAGE=africa-data-solutions`, plus the PaymentPoint/Billstack/SME Plug/VTPass
keys for whichever integrations you're using (PaymentPoint issues new virtual
accounts; Billstack is kept only to keep crediting already-issued accounts).

`docker-compose.yml` and `Caddyfile` are copied into this directory
automatically by the deploy job on every run, so you don't need to place them
there manually — but the very first deploy will fail to find `.env` if you
haven't created it yet, so do this step before the first push to `main`.

### 5. First deploy

Push to `main` (or run the workflow manually via Actions → Build, Push &
Deploy → Run workflow). Watch the Actions tab; the `deploy` job's SSH output
shows the compose pull/up. After it finishes, `docker compose ps` on the
server should show `db`, `app`, and `caddy` all healthy, and
`https://api.africadatasolutions.org/api/v1/health` should respond.

## Local development

```bash
docker compose -f docker-compose.dev.yml up --build
```

Builds the image from source, runs Postgres + the app, exposes the app on
`localhost:5000` — no Caddy, no GHCR, no real secrets needed.

## Security note

`src/main/resources/application.yml` no longer has hardcoded fallback values
for `JWT_SECRET`, the database password, or the Billstack/SME Plug/VTPass API
keys — those `${VAR}` placeholders now have no default, so the app fails to
start if one is missing from `.env` rather than silently falling back to a
committed value. Local dev (`docker-compose.dev.yml`) sets its own
placeholder values for these directly in the compose file.

The values that were previously hardcoded there are still exposed in git
history even though the file no longer contains them — rotate any of them
that were real production credentials. As of 2026-09-02: `VTPASS_PUBLIC_KEY`
and `VTPASS_SECRET_KEY` in `.env` are new values, not the exposed ones, but
`VTPASS_API_KEY` is still the same value that was hardcoded in this file —
rotate it from the VTPass dashboard when possible. JWT_SECRET, the database
password, and the Billstack/SME Plug keys have not been checked/rotated.
