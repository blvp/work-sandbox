# Squid HTTP/HTTPS Proxy — Ansible Playbook

Deploys a basic authenticated Squid forward proxy on Ubuntu.
Designed for quick VPS setup (tested target: Ubuntu 22.04/24.04).

## What it does

- Installs Squid
- Sets up basic auth (htpasswd)
- Strips forwarding headers (anonymous proxy)
- Opens the proxy port via UFW (if present)

## Prerequisites

### 1. Create a VPS on geo.hosting

1. Sign up at [geo.hosting](https://geo.hosting) and create a new VPS
2. Choose **Ubuntu 22.04** or **24.04** as the OS
3. Note your server's **IP address** and **root password** from the dashboard

### 2. Install Ansible on your laptop

**macOS:**

```bash
brew install ansible
```

**Ubuntu/Debian:**

```bash
sudo apt update && sudo apt install -y ansible
```

**Windows (WSL):**

```bash
# Install WSL first if you haven't: wsl --install
# Then inside WSL (Ubuntu):
sudo apt update && sudo apt install -y ansible
```

Verify it works:

```bash
ansible --version
```

### 3. Set up SSH access to your VPS

```bash
# Generate an SSH key if you don't have one
ssh-keygen -t ed25519 -C "your_email@example.com"

# Copy your key to the VPS (enter root password when prompted)
ssh-copy-id root@YOUR_VPS_IP

# Verify you can connect without a password
ssh root@YOUR_VPS_IP
```

## Setup

### 1. Clone this repo

```bash
git clone https://github.com/blvp/work-sandbox.git
cd work-sandbox
```

### 2. Add your VPS to the inventory

Edit `inventory.ini` and add your server:

```ini
[proxy]
proxy1 ansible_host=YOUR_VPS_IP ansible_user=root
```

### 3. Run the playbook

```bash
ansible-playbook -i inventory.ini playbook.yml \
  -e proxy_password_plain=YOUR_SECURE_PASSWORD
```

Replace `YOUR_SECURE_PASSWORD` with a strong password of your choice.

### 4. Verify it works

```bash
curl -x http://proxyuser:YOUR_SECURE_PASSWORD@YOUR_VPS_IP:3128 https://httpbin.org/ip
```

You should see your VPS IP in the response, not your laptop's IP.

## Use with Claude Code

Set the proxy environment variables before launching Claude:

```bash
export HTTP_PROXY=http://proxyuser:YOUR_SECURE_PASSWORD@YOUR_VPS_IP:3128
export HTTPS_PROXY=http://proxyuser:YOUR_SECURE_PASSWORD@YOUR_VPS_IP:3128
claude
```

Or persist in `~/.claude/settings.json` so it applies every session:

```json
{
  "env": {
    "HTTP_PROXY": "http://proxyuser:YOUR_SECURE_PASSWORD@YOUR_VPS_IP:3128",
    "HTTPS_PROXY": "http://proxyuser:YOUR_SECURE_PASSWORD@YOUR_VPS_IP:3128"
  }
}
```

## Variables

| Variable               | Default     | Description                      |
|------------------------|-------------|----------------------------------|
| `proxy_port`           | `3128`      | Squid listen port                |
| `proxy_user`           | `proxyuser` | Basic auth username              |
| `proxy_password_plain` | (required)  | Basic auth password              |
| `allowed_cidrs`        | `0.0.0.0/0` | Restrict to your IP for security |

## Lock down to your IP

For production use, restrict access to your IP only. In `playbook.yml`, change `allowed_cidrs`:

```yaml
allowed_cidrs:
  - "203.0.113.42/32"  # replace with your home/office IP
```

Find your current IP with:

```bash
curl -s https://httpbin.org/ip
```

Then re-run the playbook to apply the change.
