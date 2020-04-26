package javaxt.express;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

//******************************************************************************
//**  KeyManager
//******************************************************************************
/**
 *   Custom implementation of a X509KeyManager. This class is required to
 *   support keystores with multiple SSL certificates. By default, the standard
 *   Java X509KeyManager and the SunX509 implementation will pick the first
 *   aliases it finds for which there is a private key and a key of the right
 *   type for the chosen cipher suite (typically RSA). Instead, this class
 *   relies on a map of hostnames and their corresponding IP addresses. When a
 *   new SSL request is made, it checks the incoming IP address and finds the
 *   corresponding hostname. Then, it tries to find an alias in the keystore
 *   that corresponds to the hostname.
 *
 ******************************************************************************/

public class KeyManager extends X509ExtendedKeyManager { //implements X509KeyManager
    private KeyStore keyStore;
    private char[] password;
    private String alias;
    private java.util.HashMap<InetAddress, String> aliases;

    public KeyManager(KeyStore keystore, char[] password, String alias) {
        if (alias==null) throw new IllegalArgumentException("Alias is null.");
        this.keyStore = keystore;
        this.password = password;
        this.alias = alias;
    }

    public KeyManager(KeyStore keystore, char[] password, java.util.HashMap<InetAddress, String> aliases) {
        if (aliases==null || aliases.isEmpty()) throw new IllegalArgumentException("Hosts is null or empty.");
        this.keyStore = keystore;
        this.password = password;
        this.aliases = aliases;
    }

    public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
        if (alias!=null) return alias;
        else{
            try{

              //Get hostname from SSL handshake
                ExtendedSSLSession session = (ExtendedSSLSession) engine.getHandshakeSession();
                String hostname = null;
                for (SNIServerName name : session.getRequestedServerNames()) {
                    if (name.getType() == StandardConstants.SNI_HOST_NAME) {
                        hostname = ((SNIHostName) name).getAsciiName();
                        break;
                    }
                }


                String[] arr = hostname.split("\\.");
                hostname = arr[arr.length-2] + "." + arr[arr.length-1];

                //System.out.println("hostname: " + hostname);
                //System.out.println(InetAddress.getByName(hostname));
                //System.out.println(aliases.get(InetAddress.getByName(hostname)));


                return aliases.get(InetAddress.getByName(hostname));
            }
            catch(Exception e){
                return null;
            }
        }
    }

    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
        if (alias!=null) return alias;
        else return aliases.get(socket.getLocalAddress());
    }

    public PrivateKey getPrivateKey(String alias) {
        try {
            return (PrivateKey) keyStore.getKey(alias, password);
        }
        catch (Exception e) {
            return null;
        }
    }

    public X509Certificate[] getCertificateChain(String alias) {
        try {
            java.security.cert.Certificate[] certs = keyStore.getCertificateChain(alias);
            if (certs == null || certs.length == 0) return null;
            X509Certificate[] x509 = new X509Certificate[certs.length];
            for (int i = 0; i < certs.length; i++){
                x509[i] = (X509Certificate)certs[i];
            }
            return x509;
        }
        catch (Exception e) {
            return null;
        }
    }

    public String[] getServerAliases(String keyType, Principal[] issuers) {
        throw new UnsupportedOperationException("Method getServerAliases() not yet implemented.");
    }

    public String[] getClientAliases(String keyType, Principal[] issuers) {
        throw new UnsupportedOperationException("Method getClientAliases() not yet implemented.");
    }

    public String chooseClientAlias(String keyTypes[], Principal[] issuers, Socket socket) {
        throw new UnsupportedOperationException("Method chooseClientAlias() not yet implemented.");
    }

    public String chooseEngineClientAlias(String[] strings, Principal[] prncpls, SSLEngine ssle) {
        throw new UnsupportedOperationException("Method chooseEngineClientAlias() not yet implemented.");
    }
}