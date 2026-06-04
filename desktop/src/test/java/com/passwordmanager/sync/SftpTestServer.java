package com.passwordmanager.sync;

import com.jcraft.jsch.JSch;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.pubkey.AcceptAllPublickeyAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Collections;

/**
 * Embedded in-process SFTP server (Apache MINA SSHD) for real SFTP integration
 * tests. No external daemon or container required, so tests run in CI.
 *
 * <p>Authentication accepts any public key (tests control the client); the host
 * key is exposed via {@link #knownHostsEntry()} so a test can seed a trusted
 * {@code known_hosts} and exercise the production {@code StrictHostKeyChecking=yes} path.
 */
final class SftpTestServer implements AutoCloseable {

    private final SshServer sshd;
    private final int port;
    private final String knownHostsEntry;

    private SftpTestServer(SshServer sshd, int port, String knownHostsEntry) {
        this.sshd = sshd;
        this.port = port;
        this.knownHostsEntry = knownHostsEntry;
    }

    static SftpTestServer start(Path rootDir) throws Exception {
        SshServer sshd = SshServer.setUpDefaultServer();
        sshd.setHost("127.0.0.1");
        sshd.setPort(0); // ephemeral
        SimpleGeneratorHostKeyProvider hostKeys = new SimpleGeneratorHostKeyProvider();
        hostKeys.setAlgorithm("RSA");
        hostKeys.setKeySize(2048);
        sshd.setKeyPairProvider(hostKeys);
        sshd.setPublickeyAuthenticator(AcceptAllPublickeyAuthenticator.INSTANCE);
        sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        sshd.setFileSystemFactory(new VirtualFileSystemFactory(rootDir));
        sshd.start();

        KeyPair hostKey = hostKeys.loadKeys(null).iterator().next();
        StringBuilder sb = new StringBuilder();
        PublicKeyEntry.appendPublicKeyEntry(sb, hostKey.getPublic());
        return new SftpTestServer(sshd, sshd.getPort(), sb.toString());
    }

    /** Port the ephemeral server is listening on. */
    int port() { return port; }

    /** The host public key in OpenSSH form ("ssh-rsa AAAA..."), for a known_hosts line. */
    String knownHostsEntry() { return knownHostsEntry; }

    /** Writes a fresh client SSH private key (JSch/PEM format) to the given path. */
    static void writeClientKey(Path privateKeyPath) throws Exception {
        com.jcraft.jsch.KeyPair kp =
            com.jcraft.jsch.KeyPair.genKeyPair(new JSch(), com.jcraft.jsch.KeyPair.RSA, 2048);
        kp.writePrivateKey(privateKeyPath.toString());
        kp.dispose();
    }

    @Override
    public void close() throws Exception {
        sshd.stop(true);
    }
}
