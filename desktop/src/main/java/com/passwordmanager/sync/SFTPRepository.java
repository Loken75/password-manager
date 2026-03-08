package com.passwordmanager.sync;

import com.jcraft.jsch.*;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages remote file operations via SFTP using JSch.
 * Connects using SSH private key only (no password auth).
 * Host key verification uses ~/.ssh/known_hosts.
 */
public class SFTPRepository implements RemoteSyncRepository {
    private static final Logger LOGGER = Logger.getLogger(SFTPRepository.class.getName());
    private Session session;
    private ChannelSftp sftpChannel;
    private final String host;
    private final int port;
    private final String user;
    private final String privateKeyPath;
    private final String remotePath;

    public SFTPRepository(String host, int port, String user, String privateKeyPath, String remotePath) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.privateKeyPath = privateKeyPath;
        this.remotePath = remotePath;
    }

    public void connect() throws JSchException {
        JSch jsch = new JSch();
        jsch.addIdentity(privateKeyPath);
        LOGGER.fine("Using SSH key authentication");

        // SEC-01: Ensure known_hosts exists so StrictHostKeyChecking can work.
        // Without this file, all connections are rejected with no guidance.
        String knownHostsPath = System.getProperty("user.home") + File.separator + ".ssh" + File.separator + "known_hosts";
        File knownHostsFile = new File(knownHostsPath);
        if (!knownHostsFile.exists()) {
            try {
                knownHostsFile.getParentFile().mkdirs();
                if (!knownHostsFile.createNewFile()) {
                    LOGGER.warning("Could not create known_hosts file");
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to create known_hosts file", e);
            }
        }
        jsch.setKnownHosts(knownHostsPath);

        session = jsch.getSession(user, host, port);
        session.setConfig("StrictHostKeyChecking", "yes");
        try {
            session.connect(30000);
        } catch (JSchException e) {
            if (e.getMessage() != null && e.getMessage().contains("reject HostKey")) {
                throw new JSchException(
                    "Host key rejected for " + host + ". Add it with: ssh-keyscan -p " + port
                    + " " + host + " >> ~/.ssh/known_hosts", e);
            }
            throw e;
        }

        try {
            Channel channel = session.openChannel("sftp");
            channel.connect(10000);
            sftpChannel = (ChannelSftp) channel;
        } catch (JSchException e) {
            // Cleanup session if channel connection fails
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            session = null;
            throw e;
        }
    }

    public boolean isConnected() {
        return session != null && session.isConnected()
            && sftpChannel != null && sftpChannel.isConnected();
    }

    public void disconnect() {
        if (sftpChannel != null && sftpChannel.isConnected()) sftpChannel.disconnect();
        if (session != null && session.isConnected()) session.disconnect();
    }

    public void uploadFile(String localPath, String remoteFilename) throws SftpException {
        validateFilename(remoteFilename);
        validateLocalPath(localPath);
        sftpChannel.put(localPath, remotePath + "/" + remoteFilename, ChannelSftp.OVERWRITE);
    }

    private static final long MAX_DOWNLOAD_SIZE = 50 * 1024 * 1024; // 50 MB

    public void downloadFile(String remoteFilename, String localPath) throws SftpException {
        validateFilename(remoteFilename);
        // Check remote file size before downloading
        SftpATTRS attrs = sftpChannel.lstat(remotePath + "/" + remoteFilename);
        if (attrs.getSize() > MAX_DOWNLOAD_SIZE) {
            throw new SftpException(0, "Remote file exceeds maximum size (" + MAX_DOWNLOAD_SIZE / (1024 * 1024) + " MB)");
        }
        sftpChannel.get(remotePath + "/" + remoteFilename, localPath);
    }

    public boolean remoteFileExists(String remoteFilename) {
        validateFilename(remoteFilename);
        try {
            sftpChannel.lstat(remotePath + "/" + remoteFilename);
            return true;
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return false;
            }
            // SYNC-06: Propagate permission denied and other errors instead of
            // silently treating them as "file absent" (which could cause overwrites).
            throw new RuntimeException("SFTP error checking " + remoteFilename + ": " + e.getMessage(), e);
        }
    }

    public long getRemoteLastModified(String remoteFilename) {
        validateFilename(remoteFilename);
        try {
            SftpATTRS attrs = sftpChannel.lstat(remotePath + "/" + remoteFilename);
            return (long) attrs.getMTime() * 1000L;
        } catch (SftpException e) {
            return 0;
        }
    }

    public boolean testConnection() {
        try {
            connect();
            boolean ok = isConnected();
            disconnect();
            return ok;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Connection test failed", e);
            return false;
        }
    }

    private static void validateLocalPath(String localPath) {
        if (localPath == null || localPath.isEmpty()) {
            throw new IllegalArgumentException("Local path must not be empty");
        }
        try {
            File localFile = new File(localPath).getCanonicalFile();
            String vaultDir = new File(System.getProperty("user.home"), ".passwordmanager")
                    .getCanonicalPath();
            if (!localFile.getPath().startsWith(vaultDir + File.separator) &&
                    !localFile.getPath().equals(vaultDir)) {
                throw new IllegalArgumentException("Local path escapes vault directory: " + localPath);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot resolve local path: " + localPath, e);
        }
    }

    private static void validateFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("Remote filename must not be empty");
        }
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")
                || filename.startsWith("~")) {
            throw new IllegalArgumentException("Invalid remote filename: path traversal detected");
        }
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getUser() { return user; }
    public String getRemotePath() { return remotePath; }
}
