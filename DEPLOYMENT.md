# Deployment

CI/CD: on every push to `main`, GitHub Actions runs the test suite against a
throwaway Postgres, builds a Docker image, pushes it to GHCR
(`ghcr.io/<owner>/africa-data-solutions`), then SSHes into the Oracle server
and redeploys via `docker compose`. Workflow: `.github/workflows/deploy.yml`.

## One-time setup

### 1. Oracle Cloud server

- A `VM.Standard.E2.1.Micro` (x86_64/amd64) compute instance with a public IP
  — the CI build targets `linux/amd64` specifically, matching this shape. If
  you later move to an Ampere A1 (arm64) instance instead, change
  `platforms: linux/amd64` to `linux/arm64` in
  `.github/workflows/deploy.yml` (and add back `docker/setup-qemu-action`
  before the buildx step, since the GitHub runner itself is amd64 and would
  need to cross-compile).
- Only **1 GB RAM** on this shape — tight for Postgres + the JVM + Caddy
  together. The Dockerfile already sizes the JVM heap as a percentage of
  available memory rather than a fixed value, but you'll likely still want a
  swap file so a memory spike doesn't OOM-kill a container:
  ```bash
  sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
  sudo mkswap /swapfile && sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
  ```
- **Two separate firewalls both block traffic by default on OCI — you need to
  open both, or Caddy will never get a Let's Encrypt certificate:**
  1. The Security List / Network Security Group on the VCN's subnet (in the
     OCI console) — add ingress rules for TCP 22, 80, 443 from `0.0.0.0/0`.
  2. On the instance itself: Oracle's official Ubuntu marketplace image
     pre-configures `iptables` (via netfilter-persistent) to only allow SSH
     inbound — separate from `ufw`, and easy to miss since `ufw status` alone
     can look fine while this still blocks 80/443:
     ```bash
     sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
     sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
     sudo netfilter-persistent save
     ```
     Also check `sudo ufw status` — if `ufw` is active, add
     `sudo ufw allow 80,443/tcp` too.
- Install Docker + the Compose plugin:
  ```bash
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker $USER   # log out/in after this
  ```
- `africadatasolutions.org` already hosts the frontend, so don't repoint the
  root domain — in Hostinger's DNS zone editor, add a new **A record**: name
  `api`, value the server's public IP. This gives the backend
  `api.africadatasolutions.org` without touching the existing frontend
  records. Caddy needs this DNS record live before it can issue a Let's
  Encrypt certificate.

### 2. Deploy SSH key

Generate a dedicated key pair for GitHub Actions (don't reuse your personal key):

```bash
ssh-keygen -t ed25519 -f deploy_key -N ""
```

- Append `deploy_key.pub` to `~/.ssh/authorized_keys` for the deploy user on
  the Oracle server.
- Keep `deploy_key` (the private half) for the GitHub secret below.

### 3. GitHub repository secrets

Settings → Secrets and variables → Actions → New repository secret:

| Secret | Value |
|---|---|
| `ORACLE_HOST` | Server's public IP or hostname |
| `ORACLE_USER` | `ubuntu` |
| `ORACLE_SSH_KEY` | Contents of the private `deploy_key` file |
| `ORACLE_SSH_PORT` | Only if not 22 |

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
`GHCR_IMAGE=africa-data-solutions`, plus the Billstack/SME Plug/VTPass keys
for whichever integrations you're using.

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

`src/main/resources/application.yml` currently has real-looking fallback
values hardcoded for `JWT_SECRET`, the database password, and the
Billstack/SME Plug/VTPass API keys (used only if the corresponding env var
isn't set). Since this file is committed to git, anyone with repo access —
now or from git history, even if removed later — has those values. Once
production is running purely off `.env` (which this setup does), those
defaults are dead code in that path but still exposed in the repo. Rotate any
of them that are real credentials, and remove the hardcoded fallbacks.
