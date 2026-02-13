package com.passwordmanager.sync;

import com.jcraft.jsch.*;

import java.util.Properties;

/**
 * Manages remote file operations via SFTP using JSch.
 * Connects using SSH private key only (no password auth).
 */
public class SFTPRepository {
    private Session session;
    private ChannelSftp sftpChannel;
    private String host;
    private int port;
    private String user;
    private String privateKeyPath;
    private String remotePath;

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

        session = jsch.getSession(user, host, port);
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect(30000);

        Channel channel = session.openChannel("sftp");
        channel.connect(10000);
        sftpChannel = (ChannelSftp) channel;
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
        sftpChannel.put(localPath, remotePath + "/" + remoteFilename, ChannelSftp.OVERWRITE);
    }

    public void downloadFile(String remoteFilename, String localPath) throws SftpException {
        sftpChannel.get(remotePath + "/" + remoteFilename, localPath);
    }

    public boolean remoteFileExists(String remoteFilename) {
        try {
            sftpChannel.lstat(remotePath + "/" + remoteFilename);
            return true;
        } catch (SftpException e) {
            return false;
        }
    }

    public long getRemoteLastModified(String remoteFilename) {
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
            return false;
        }
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getUser() { return user; }
    public String getRemotePath() { return remotePath; }
}
