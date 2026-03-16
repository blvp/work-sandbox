# Squid HTTP/HTTPS Proxy — Ansible Playbook

Deploys a basic authenticated Squid forward proxy on Ubuntu.
Designed for quick VPS setup (tested target: Ubuntu 22.04/24.04).

## What it does

- Installs Squid
- Sets up basic auth (htpasswd)
- Strips forwarding headers (anonymous proxy)
- Opens the proxy port via UFW (if present)

## Usage

```bash
# 1. Edit inventory with your VPS IP
vim inventory.ini

# 2. Run it
ansible-playbook -i inventory.ini playbook.yml \
  -e proxy_password_plain=YOUR_SECURE_PASSWORD

# 3. Test from your machine
curl -x http://proxyuser:YOUR_SECURE_PASSWORD@YOUR_VPS_IP:3128 https://httpbin.org/ip
```

## Use with Claude Code

```bash
export HTTPS_PROXY=http://proxyuser:YOUR_SECURE_PASSWORD@YOUR_VPS_IP:3128
export HTTP_PROXY=http://proxyuser:YOUR_SECURE_PASSWORD@YOUR_VPS_IP:3128
claude
```

Or persist in `~/.claude/settings.json`:

```json
{
  "env": {
    "HTTP_PROXY": "http://proxyuser:pass@YOUR_VPS_IP:3128",
    "HTTPS_PROXY": "http://proxyuser:pass@YOUR_VPS_IP:3128"
  }
}
```

## Variables

|Variable              |Default    |Description                     |
|----------------------|-----------|--------------------------------|
|`proxy_port`          |`3128`     |Squid listen port               |
|`proxy_user`          |`proxyuser`|Basic auth username             |
|`proxy_password_plain`|(required) |Basic auth password             |
|`allowed_cidrs`       |`0.0.0.0/0`|Restrict to your IP for security|

## Lock down to your IP

In `playbook.yml`, change `allowed_cidrs`:

```yaml
allowed_cidrs:
  - "203.0.113.42/32"  # your home IP
```
