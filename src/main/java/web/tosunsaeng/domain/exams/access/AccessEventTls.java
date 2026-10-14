package web.tosunsaeng.domain.exams.access;

import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;

/** Dedicated TLS-only listener, loaded from a mounted PKCS12 file; not the workload JWT key. */
public final class AccessEventTls {
    private AccessEventTls() {}
    public static Connector connector(AccessEventProperties p,int publicPort) {
        p.validate(publicPort);
        if(!p.isReceiverEnabled()) throw new IllegalStateException("Billing access receiver is disabled");
        try {
            if(p.getTlsKeyAlias()==null || p.getTlsKeyAlias().isBlank() || p.getTlsKeyStorePassword()==null
                    || p.getTlsKeyStorePassword().isBlank()) throw new IllegalArgumentException();
            var path=Path.of(p.getTlsKeyStore()); if(!path.isAbsolute()) throw new IllegalArgumentException();
            var store=KeyStore.getInstance("PKCS12");
            try(var in=Files.newInputStream(path)){store.load(in,p.getTlsKeyStorePassword().toCharArray());}
            var key=store.getKey(p.getTlsKeyAlias(),p.getTlsKeyStorePassword().toCharArray());
            if(!(key instanceof PrivateKey)) throw new IllegalArgumentException();
            var chain=store.getCertificateChain(p.getTlsKeyAlias()); if(chain==null || chain.length==0) throw new IllegalArgumentException();
            ((X509Certificate)chain[0]).checkValidity();
            var manager=javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
            manager.init(store,p.getTlsKeyStorePassword().toCharArray());
            var ssl=new SSLHostConfig(); ssl.setProtocols("TLSv1.2,TLSv1.3");
            var certificate=new SSLHostConfigCertificate(ssl,SSLHostConfigCertificate.Type.UNDEFINED);
            certificate.setCertificateKeystore(store);certificate.setCertificateKeyAlias(p.getTlsKeyAlias());
            certificate.setCertificateKeyPassword(p.getTlsKeyStorePassword());ssl.addCertificate(certificate);
            var connector=new Connector("org.apache.coyote.http11.Http11NioProtocol");
            connector.setPort(p.getPort());connector.setSecure(true);connector.setScheme("https");
            connector.setProperty("SSLEnabled","true");connector.setProperty("maxThreads","10");
            connector.setProperty("maxConnections","50");connector.setProperty("connectionTimeout","5000");
            connector.addSslHostConfig(ssl);return connector;
        }catch(Exception invalid){throw new IllegalStateException("Billing access TLS configuration is invalid");}
    }
}
