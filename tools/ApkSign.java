/*
 * apksigner, in about a hundred lines.
 *
 * The Android SDK ships this as a wrapper around com.android.apksig; the library itself is on
 * Maven Central, so the SDK is not needed. Two modes:
 *
 *   sign   <keystore> <storepass> <alias> <keypass> <in.apk> <out.apk> <minSdk> <v1> <v2>
 *   verify <apk> <minSdk>
 *
 * The build signs in two passes. Pass one adds the v1 (JAR) signature, which inserts entries
 * into the zip and therefore moves everything after them. The result is then aligned, and pass
 * two adds the v2 signature, which only splices a signing block in front of the central
 * directory and so leaves every entry exactly where alignment put it.
 */

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ApkSign {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
        } else if (args[0].equals("sign") && args.length == 10) {
            sign(args);
        } else if (args[0].equals("verify") && args.length == 3) {
            verify(new File(args[1]), Integer.parseInt(args[2]));
        } else {
            usage();
        }
    }

    private static void usage() {
        System.err.println("usage: ApkSign sign <keystore> <storepass> <alias> <keypass> "
                + "<in.apk> <out.apk> <minSdk> <v1:true|false> <v2:true|false>");
        System.err.println("       ApkSign verify <apk> <minSdk>");
        System.exit(2);
    }

    private static void sign(String[] a) throws Exception {
        File keystoreFile = new File(a[1]);
        char[] storePass = a[2].toCharArray();
        String alias = a[3];
        char[] keyPass = a[4].toCharArray();
        File in = new File(a[5]);
        File out = new File(a[6]);
        int minSdk = Integer.parseInt(a[7]);
        boolean v1 = Boolean.parseBoolean(a[8]);
        boolean v2 = Boolean.parseBoolean(a[9]);

        KeyStore keystore = KeyStore.getInstance("PKCS12");
        InputStream ksIn = new FileInputStream(keystoreFile);
        try {
            keystore.load(ksIn, storePass);
        } finally {
            ksIn.close();
        }

        PrivateKey key = (PrivateKey) keystore.getKey(alias, keyPass);
        if (key == null) throw new IllegalStateException("no key for alias " + alias);

        java.security.cert.Certificate[] chain = keystore.getCertificateChain(alias);
        if (chain == null || chain.length == 0) {
            throw new IllegalStateException("no certificate chain for alias " + alias);
        }
        List<X509Certificate> certs = new ArrayList<X509Certificate>(chain.length);
        for (int i = 0; i < chain.length; i++) {
            certs.add((X509Certificate) chain[i]);
        }

        ApkSigner.SignerConfig signer =
                new ApkSigner.SignerConfig.Builder("nur", key, certs).build();

        new ApkSigner.Builder(Collections.singletonList(signer))
                .setInputApk(in)
                .setOutputApk(out)
                .setMinSdkVersion(minSdk)
                .setV1SigningEnabled(v1)
                .setV2SigningEnabled(v2)
                .setOtherSignersSignaturesPreserved(false)
                .build()
                .sign();
    }

    private static void verify(File apk, int minSdk) throws Exception {
        ApkVerifier.Result result = new ApkVerifier.Builder(apk)
                .setMinCheckedPlatformVersion(minSdk)
                .build()
                .verify();

        for (ApkVerifier.IssueWithParams issue : result.getErrors()) {
            System.err.println("error: " + issue);
        }
        for (ApkVerifier.IssueWithParams issue : result.getWarnings()) {
            System.err.println("warning: " + issue);
        }

        if (!result.isVerified()) {
            System.err.println("APK signature did NOT verify");
            System.exit(1);
        }

        System.out.println("signature verifies"
                + "  v1=" + result.isVerifiedUsingV1Scheme()
                + "  v2=" + result.isVerifiedUsingV2Scheme());

        List<X509Certificate> signers = result.getSignerCertificates();
        for (X509Certificate cert : signers) {
            System.out.println("signer: " + cert.getSubjectDN());
        }
    }
}
