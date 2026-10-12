package com.example.app.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * 試験の中で作る、一時的な {@code localhost} の自己署名の証明書。証明書と秘密鍵は PEM のファイルに書き出す。
 *
 * <p>証明書はリポジトリに入れず、試験のたびに作る。
 */
public final class TestCertificate {
    private final X509Certificate certificate;
    private final Path certPath;
    private final Path keyPath;

    private TestCertificate(X509Certificate certificate, Path certPath, Path keyPath) {
        this.certificate = certificate;
        this.certPath = certPath;
        this.keyPath = keyPath;
    }

    /** 証明書と秘密鍵を作り、{@code dir} に {@code localhost.pem} と {@code localhost-key.pem} として書き出す。 */
    public static TestCertificate create(Path dir) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();

            X500Name name = new X500Name("CN=localhost");
            Instant now = Instant.now();
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    name,
                    BigInteger.valueOf(now.toEpochMilli()),
                    Date.from(now.minus(Duration.ofDays(1))),
                    Date.from(now.plus(Duration.ofDays(1))),
                    name,
                    keyPair.getPublic());
            builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(new GeneralName[] {
                new GeneralName(GeneralName.dNSName, "localhost"),
                new GeneralName(GeneralName.iPAddress, InetAddress.getLoopbackAddress().getHostAddress()),
            }));
            X509Certificate certificate = new JcaX509CertificateConverter()
                    .getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate())));

            Path certPath = dir.resolve("localhost.pem");
            Path keyPath = dir.resolve("localhost-key.pem");
            writePem(certPath, certificate);
            writePem(keyPath, keyPair.getPrivate());
            return new TestCertificate(certificate, certPath, keyPath);
        } catch (GeneralSecurityException | OperatorCreationException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path certPath() {
        return certPath;
    }

    public Path keyPath() {
        return keyPath;
    }

    /** この証明書だけを信頼する SSLContext。 */
    public SSLContext trustingSslContext() {
        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            trustStore.setCertificateEntry("localhost", certificate);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, tmf.getTrustManagers(), null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writePem(Path path, Object object) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.US_ASCII);
                JcaPEMWriter pem = new JcaPEMWriter(writer)) {
            pem.writeObject(object);
        }
    }
}
