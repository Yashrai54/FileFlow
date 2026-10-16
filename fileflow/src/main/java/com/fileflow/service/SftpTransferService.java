package com.fileflow.service;

import com.fileflow.models.Direction;
import com.fileflow.models.TransferJob;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteFile;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

@Service
public class SftpTransferService {
    private static final Logger log = LoggerFactory.getLogger(SftpTransferService.class);
        private static final String PART_SUFFIX = ".part";

    private final String password;
    private final String knownHostsFile;

    public SftpTransferService(@Value("${fileflow.sftp.password}") String password,
                               @Value("${fileflow.sftp.known-hosts}") String knownHostsFile) {
        this.password = password;
        this.knownHostsFile = knownHostsFile;
    }

    public TransferResult transfer(TransferJob job) throws IOException{
        try(SSHClient ssh = new SSHClient()) {
            configureHostKeys(ssh);
             ssh.setConnectTimeout(10_000);
            ssh.setTimeout(30_000);
            ssh.connect(job.getHost(), job.getPort());
            ssh.authPassword(job.getUsername(), password);

            try (SFTPClient sftp = ssh.newSFTPClient()) {
                return job.getDirection() == Direction.INBOUND
                        ? download(sftp, job)
                        : upload(sftp, job);
            }
        }
    }
    private void configureHostKeys(SSHClient ssh) throws IOException {
        if (knownHostsFile != null && !knownHostsFile.isBlank()) {
            ssh.loadKnownHosts(new File(knownHostsFile));
        } else {
            log.warn("No known_hosts file configured: accepting ANY server host key. "
                    + "This is only acceptable for local development.");
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
        }
    }

    private TransferResult download(SFTPClient sftp, TransferJob job) throws IOException{
                Path localDir = Path.of(job.getLocalPath());
                Files.createDirectories(localDir);

                int files =0;
                long bytes = 0;

                for(RemoteResourceInfo remoteFile : sftp.ls(job.getRemotePath())) {
                    if (!remoteFile.isRegularFile()) {
                        continue;
                    }
                    String remoteFileName = Path.of(remoteFile.getName()).getFileName().toString();
                    Path localFile = localDir.resolve(remoteFileName);
                    Path tempLocalFile = localDir.resolve(remoteFileName + PART_SUFFIX);

                    sftp.get(remoteFile.getPath(), tempLocalFile.toString());

                    if(!sha256Remote(sftp, remoteFile.getPath()).equals(sha256Local(tempLocalFile))) {
                                        Files.deleteIfExists(tempLocalFile);

                        throw new IOException("Checksum mismatch for file " + remoteFileName);
                    }
                    Files.move(tempLocalFile, localFile, StandardCopyOption.REPLACE_EXISTING);
            files++;
            bytes += Files.size(localFile);
            log.info("Downloaded and verified {}", remoteFileName);
    }
            return new TransferResult(files, bytes);

}

private TransferResult upload(SFTPClient sftp, TransferJob job) throws IOException {
        Path localDir = Path.of(job.getLocalPath());
        if (!Files.isDirectory(localDir)) {
            throw new IllegalArgumentException("Local directory not found: " + localDir);
        }
        sftp.mkdirs(job.getRemotePath());

        List<Path> localFiles;
        try (Stream<Path> stream = Files.list(localDir)) {
            localFiles = stream.filter(Files::isRegularFile).toList();
        }

        int files = 0;
        long bytes = 0;
        for (Path local : localFiles) {
            String name = local.getFileName().toString();
            String finalRemote = joinRemote(job.getRemotePath(), name);
            String partRemote = finalRemote + PART_SUFFIX;

            sftp.put(local.toString(), partRemote);

            if (!sha256Remote(sftp, partRemote).equals(sha256Local(local))) {
                sftp.rm(partRemote);
                throw new IOException("Checksum mismatch for " + name);
            }
            if (sftp.statExistence(finalRemote) != null) {
                sftp.rm(finalRemote);
            }
            sftp.rename(partRemote, finalRemote);
            files++;
            bytes += Files.size(local);
            log.info("Uploaded and verified {}", name);
        }
        return new TransferResult(files, bytes);
    }

    private static String joinRemote(String dir, String name) {
        return dir.endsWith("/") ? dir + name : dir + "/" + name;
    }

    /** Re-reads the file from the server and hashes it: slower, but a true end-to-end check. */
    private static String sha256Remote(SFTPClient sftp, String remotePath) throws IOException {
        try (RemoteFile file = sftp.open(remotePath);
             InputStream in = file.new RemoteFileInputStream(0)) {
            return sha256(in);
        }
    }

    private static String sha256Local(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return sha256(in);
        }
    }

    private static String sha256(InputStream in) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
