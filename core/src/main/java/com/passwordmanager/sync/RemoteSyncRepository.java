package com.passwordmanager.sync;

/**
 * Abstraction for remote vault file operations (e.g. SFTP).
 * Implemented by {@link SFTPRepository} for production use.
 */
public interface RemoteSyncRepository {

    /** Opens a connection to the remote server. */
    void connect() throws Exception;

    /** Closes the connection to the remote server. */
    void disconnect();

    /** Returns true if the remote file exists. */
    boolean remoteFileExists(String remoteFilename);

    /** Returns the last-modified timestamp in millis for the remote file, or 0 if absent. */
    long getRemoteLastModified(String remoteFilename);

    /** Uploads a local file to the remote server. */
    void uploadFile(String localPath, String remoteFilename) throws Exception;

    /** Downloads a remote file to a local path. */
    void downloadFile(String remoteFilename, String localPath) throws Exception;

    /** Tests whether a connection can be established. */
    boolean testConnection();
}
