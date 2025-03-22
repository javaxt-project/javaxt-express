package javaxt.express;
import java.util.*;
import javax.net.ssl.*;
import java.net.Socket;
import java.net.InetAddress;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

//******************************************************************************
//**  KeyManager
//******************************************************************************
/**
 *   Custom implementation of a X509KeyManager. This class is used to support
 *   keystores with multiple SSL certificates. By default, the standard Java
 *   X509KeyManager and the SunX509 implementation will pick the first alias
 *   it finds for which there is a private key and a key type that matches
 *   the chosen cipher suite (typically RSA).
 *
 *   Instead, this class tries to find an alias in the keystore that best
 *   matches the requested hostname found in the SSL handshake. This assumes
 *   that the keystore aliases contain hostnames (e.g. "www.acme.com") or top
 *   level domain names (e.g. "acme.com").
 *
 *   In addition, this class requires a mapping of aliases/hostnames to IP
 *   addresses on the host server. This is required for the chooseServerAlias()
 *   method which is called early in the SSL handshake process (well before
 *   the hostname is known). When the chooseServerAlias() method is called, all
 *   we have is a IP address to identify the alias so a hashmap is used to tie
 *   a domain name to an IP address.
 *
 ******************************************************************************/

public class KeyManager extends X509ExtendedKeyManager { //implements X509KeyManager
    private KeyStore keyStore;
    private char[] password;
    private HashMap<InetAddress, String> aliases;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public KeyManager(KeyStore keystore, char[] password, HashMap<InetAddress, String> aliases) {
        if (aliases==null || aliases.isEmpty()) throw new IllegalArgumentException("Hosts is null or empty.");
        this.keyStore = keystore;
        this.password = password;
        this.aliases = aliases;
    }


  //**************************************************************************
  //** chooseEngineServerAlias
  //**************************************************************************
  /** Returns an alias in the keystore that best matches the requested
   *  hostname found in the SSL handshake
   *  @param keyType Not used
   *  @param issuers Not used
   *  @param engine SSLEngine with a handshake session
   */
    public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
        try{

          //Get hostname from SSL handshake (www.acme.com)
            String hostname = null;
            ExtendedSSLSession session = (ExtendedSSLSession) engine.getHandshakeSession();
            for (SNIServerName name : session.getRequestedServerNames()) {
                if (name.getType() == StandardConstants.SNI_HOST_NAME) {
                    hostname = ((SNIHostName) name).getAsciiName();
                    break;
                }
            }
            if (hostname==null) return null;
            else hostname = hostname.toLowerCase();


          //Get top-level domain name (acme.com)
            String[] arr = hostname.split("\\.");
            String domainName = arr[arr.length-2] + "." + arr[arr.length-1];



          //Special case for keystores with wildcard certs and top-level domain
          //certs. When creating aliases for wildcard certs, I use the top-level
          //domain name as the alias (e.g. "acme.com" alias for a "*.acme.com"
          //wildcard cert). However, in some cases we also want a cert for the
          //top-level domain (e.g. "acme.com"). So for top-level domain certs,
          //I use an underscore prefix (e.g. "_acme.com" alias for a "acme.com"
          //cert). The following code will search for an alias with an
          //underscore prefix whenever a top-level domain name is requested.
            if (domainName.equals(hostname)){
                Enumeration enumeration = keyStore.aliases();
                while (enumeration.hasMoreElements()) {
                    String alias = (String) enumeration.nextElement();
                    if (alias.equals("_"+domainName)) return alias;
                }
            }



          //Return the alias associated with the IP address of the top-level
          //domain name.
            return aliases.get(InetAddress.getByName(domainName));

        }
        catch(Exception e){
            return null;
        }
    }


  //**************************************************************************
  //** chooseEngineServerAlias
  //**************************************************************************
  /** Returns an alias that best matches the given HTTP socket.
   *  @param keyType Not used
   *  @param issuers Not used
   *  @param socket HTTP socket
   */
    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
        //System.out.println("chooseServerAlias: " + socket.getLocalAddress());
        return aliases.get(socket.getLocalAddress());
    }


  //**************************************************************************
  //** getPrivateKey
  //**************************************************************************
  /** Returns the private key from the keystore for a given alias.
   */
    public PrivateKey getPrivateKey(String alias) {
        try {
            return (PrivateKey) keyStore.getKey(alias, password);
        }
        catch (Exception e) {
            return null;
        }
    }


  //**************************************************************************
  //** getPrivateKey
  //**************************************************************************
  /** Returns the x509 certificate chain from the keystore for a given alias.
   */
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
        throw new UnsupportedOperationException("Method getServerAliases() not implemented.");
    }

    public String[] getClientAliases(String keyType, Principal[] issuers) {
        throw new UnsupportedOperationException("Method getClientAliases() not implemented.");
    }

    public String chooseClientAlias(String keyTypes[], Principal[] issuers, Socket socket) {
        throw new UnsupportedOperationException("Method chooseClientAlias() not implemented.");
    }

    public String chooseEngineClientAlias(String[] strings, Principal[] prncpls, SSLEngine ssle) {
        throw new UnsupportedOperationException("Method chooseEngineClientAlias() not implemented.");
    }
}