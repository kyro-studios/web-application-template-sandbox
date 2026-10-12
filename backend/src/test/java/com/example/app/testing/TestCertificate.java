package com.example.app.testing;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Base64;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * 試験の中で一時的に作る、localhost の自己署名の証明書。
 *
 * <p>JDK の keytool で鍵と証明書を作り、サーバーが読む PEM ファイルと、その証明書だけを信頼する
 * クライアントの {@link SSLContext} を用意する。証明書はリポジトリに入れない。
 */
public record TestCertificate(Path certificatePem, Path privateKeyPem, SSLContext clientSslContext) {

    private static final String ALIAS = "localhost";
    private static final char[] PASSWORD = "test-keystore".toCharArray();

    public static TestCertificate create(Path dir) throws Exception {
        Path keyStorePath = dir.resolve("localhost.p12");
        Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
        Process process = new ProcessBuilder(
                keytool.toString(),
                "-genkeypair",
                "-alias", ALIAS,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "1",
                "-dname", "CN=localhost",
                "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-keystore", keyStorePath.toString(),
                "-storetype", "PKCS12",
                "-storepass", new String(PASSWORD),
                "-keypass", new String(PASSWORD),
                "-noprompt")
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException("keytool failed: " + output);
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(keyStorePath)) {
            keyStore.load(in, PASSWORD);
        }
        Certificate certificate = keyStore.getCertificate(ALIAS);
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(ALIAS, PASSWORD);

        Path certificatePem = dir.resolve("localhost.pem");
        Path privateKeyPem = dir.resolve("localhost-key.pem");
        Files.writeString(certificatePem, pem("CERTIFICATE", certificate.getEncoded()));
        // PKCS#8 の形で書く
        Files.writeString(privateKeyPem, pem("PRIVATE KEY", privateKey.getEncoded()));

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry(ALIAS, certificate);
        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

        return new TestCertificate(certificatePem, privateKeyPem, sslContext);
    }

    private static String pem(String type, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }
}
