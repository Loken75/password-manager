# Guide de configuration du serveur distant (SFTP)

Ce guide explique comment configurer un serveur Ubuntu pour la synchronisation
du coffre-fort via SFTP.

---

## 1. Prerequis

- Un serveur Ubuntu (20.04+ recommande)
- Acces root ou sudo
- Le client doit disposer d'une paire de cles SSH

---

## 2. Creation de l'utilisateur dedie

```bash
sudo adduser --disabled-password vault_user
sudo mkdir -p /vault/data
sudo chown vault_user:vault_user /vault/data
sudo chmod 700 /vault/data
```

## 3. Configuration SSH (cle publique)

```bash
sudo mkdir -p /home/vault_user/.ssh
sudo chmod 700 /home/vault_user/.ssh

# Copier votre cle publique (depuis le client) :
# ssh-copy-id -i ~/.ssh/id_rsa.pub vault_user@serveur

# Ou manuellement :
sudo nano /home/vault_user/.ssh/authorized_keys
# Coller le contenu de votre fichier ~/.ssh/id_rsa.pub

sudo chmod 600 /home/vault_user/.ssh/authorized_keys
sudo chown -R vault_user:vault_user /home/vault_user/.ssh
```

## 4. Securisation de SSH

Editer `/etc/ssh/sshd_config` :

```
Port 2222
PasswordAuthentication no
PubkeyAuthentication yes
PermitRootLogin no
AllowUsers vault_user
ClientAliveInterval 300
ClientAliveCountMax 2
```

Puis redemarrer SSH :
```bash
sudo systemctl restart sshd
```

## 5. Firewall (UFW)

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 2222/tcp
sudo ufw enable
```

## 6. Fail2Ban (protection brute-force)

```bash
sudo apt update && sudo apt install -y fail2ban
```

Creer `/etc/fail2ban/jail.local` :
```ini
[sshd]
enabled = true
port = 2222
filter = sshd
logpath = /var/log/auth.log
maxretry = 3
bantime = 3600
```

```bash
sudo systemctl enable fail2ban
sudo systemctl start fail2ban
```

## 7. Repertoire de logs (optionnel)

```bash
sudo mkdir -p /var/log/password-manager
sudo touch /var/log/password-manager/audit.log
sudo chmod 600 /var/log/password-manager/audit.log
```

## 8. Configuration dans l'application

Dans les parametres de l'application (Fichier > Parametres > Synchronisation) :

- **Mode** : Serveur distant
- **Hote** : adresse IP ou nom de domaine du serveur
- **Port** : 2222 (ou le port configure)
- **Utilisateur SSH** : vault_user
- **Cle privee SSH** : chemin vers votre cle privee (ex: /home/user/.ssh/id_rsa)
- **Repertoire distant** : /vault/data

Cliquez sur "Tester la connexion" pour verifier.

## 9. Generation de cles SSH (si necessaire)

Sur le client :
```bash
ssh-keygen -t rsa -b 4096 -f ~/.ssh/id_rsa_vault
```

Puis copier la cle publique sur le serveur :
```bash
ssh-copy-id -i ~/.ssh/id_rsa_vault.pub -p 2222 vault_user@serveur
```

Dans l'application, renseigner le chemin : `~/.ssh/id_rsa_vault`
