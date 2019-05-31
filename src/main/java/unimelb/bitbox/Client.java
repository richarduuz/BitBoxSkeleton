package unimelb.bitbox;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;

import java.io.*;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;

public class Client {

    public static void main(String[] args){
        //Object that will store the parsed command line arguments
        cmdLineArgs argsBean = new cmdLineArgs();

        //Parser provided by args4j
        CmdLineParser parser = new CmdLineParser(argsBean);
        try{
            parser.parseArgument(args);
            String server = argsBean.getServer();
            String command = argsBean.getCommand();
            switch(command){
                case("list_peers"):{
                    command = "LIST_PEERS_REQUEST";
                    break;
                }
                case("connect_peer"):{
                    command = "CONNECT_PEER_REQUEST";
                    break;
                }
                case("disconnect_peer"):{
                    command = "DISCONNECT_PEER_REQUEST";
                    break;
                }

            }
            String peer_host = null;
            long peer_port = 0;
            if (command.equals("CONNECT_PEER_REQUEST") || command.equals("DISCONNECT_PEER_REQUEST")){
                String peer = argsBean.getPeer();
                peer_host = peer.split(":")[0];
                if (peer_host.equals("localhost")){
                    peer_host = "127.0.0.1";
                }
                peer_port = Long.parseLong(peer.split(":")[1]);
            }
            String server_host = server.split(":")[0];
            long server_port = Long.parseLong(server.split(":")[1]);
            Socket clientSocket = new Socket(server_host, (int)server_port);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"));
            JSONObject auth_request = new JSONObject();
            auth_request.put("command", "AUTH_REQUEST");
            auth_request.put("identity", "QT@LAPTOP-442HE0V5");
            out.write(auth_request.toJSONString() + '\n');
            out.flush();

            String serverMsg = in.readLine();
            JSONParser JsParser = new JSONParser();
            JSONObject auth_response = (JSONObject)JsParser.parse(serverMsg);
            System.out.println(auth_response.get("command") + ": " + auth_response.get("status"));
            if ((boolean)auth_response.get("status")){
                String Base64AES = (String)auth_response.get("AES128");
                //Base64 decoding
                byte[] temp = Base64.getDecoder().decode(Base64AES);
                //RSA Decryption
                PrivateKey privateKey = generatePrivateKey(System.getProperty("user.dir") + "/newkey");
//                System.out.println(System.getProperty("bitbox_client.java"

                Cipher RSAcipher = Cipher.getInstance("RSA");
                RSAcipher.init(Cipher.DECRYPT_MODE, privateKey);
                byte[] aes128 = RSAcipher.doFinal(temp);
                System.out.println("Complete RSA Decryption");
                SecretKeySpec sk = new SecretKeySpec(aes128, "AES");
                JSONObject clientRequest = new JSONObject();
                clientRequest.put("command", command);
                switch(command){
                    case("LIST_PEERS_REQUEST"):{
                        break;
                    }
                    case("CONNECT_PEER_REQUEST"):{
                        clientRequest.put("host", peer_host);
                        clientRequest.put("port", peer_port);
                        break;
                    }
                    case("DISCONNECT_PEER_REQUEST"):{
                        clientRequest.put("host", peer_host);
                        clientRequest.put("port", peer_port);
                        break;
                    }
                }
                JSONObject payload = new JSONObject();
                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.ENCRYPT_MODE, sk);

                byte[] EncryptRequest = cipher.doFinal(clientRequest.toJSONString().getBytes("UTF-8"));

                // Base64 encode
                String Base64Request = Base64.getEncoder().encodeToString(EncryptRequest);
                payload.put("payload", Base64Request);
                out.write(payload.toJSONString() + "\n");
                out.flush();

                String serverResp = in.readLine();
                JSONObject serverPayload = new JSONObject();
                serverPayload = (JSONObject)JsParser.parse(serverResp);
                byte[] encryptedResponse = Base64.getDecoder().decode((String)serverPayload.get("payload"));
                Cipher decrypt = Cipher.getInstance("AES");
                decrypt.init(Cipher.DECRYPT_MODE, sk);
                byte[] decryptedResponse = decrypt.doFinal(encryptedResponse);
                JSONObject serverResponse = (JSONObject)JsParser.parse(new String(decryptedResponse, "UTF-8"));
                System.out.println(serverResponse.get("command"));
                String serverCommand = (String)serverResponse.get("command");
                if (serverCommand.equals("CONNECT_PEER_RESPONSE") | serverCommand.equals("DISCONNECT_PEER_RESPONSE")){
                    System.out.println(serverResponse.get("message"));
                }
                else if(serverCommand.equals("LIST_PEERS_RESPONSE")){
                    JSONArray peers = (JSONArray)serverResponse.get("peers");
                    Iterator<JSONObject> peer = peers.iterator();
                    int count = 1;

                    while (peer.hasNext()){
                        JSONObject list_peer = (JSONObject)peer.next();
                        System.out.println("showing the connected peer " + count);
                        System.out.println("host: " + (String)list_peer.get("host"));
                        System.out.println("port: " + String.valueOf((long)list_peer.get("port")));
                        count += 1;
                    }
                    if (count == 1){
                        System.out.println("This peer does not connect to any peer");
                    }
                }

            }





        }catch (CmdLineException e){
            // TODO process CmdLine exception
            e.printStackTrace();
        }catch (UnknownHostException e){
            // TODO process unknown host exception
            e.printStackTrace();
        }catch (IOException e){
            // TODO process IO exception
            e.printStackTrace();
        }catch (ParseException e){
            // TODO process parser exception4e
        }catch (NoSuchAlgorithmException e){
            //TODO process no such algorithm exception
        }catch (NoSuchPaddingException e){
            //TODO process no such padding exception
        }catch (InvalidKeyException e){
            //TODO process invalid key exception
        }catch (IllegalBlockSizeException e){
            //TODO process Illegal BlockSize exception
        }catch (BadPaddingException e){
            //TODO process bad padding exception
        }

    }

    private static PrivateKey generatePrivateKey(String path){
        PrivateKey privateKey = null;
        String PKCS_1_PEM_HEADER = "-----BEGIN RSA PRIVATE KEY-----";
        String PKCS_1_PEM_FOOTER = "-----END RSA PRIVATE KEY-----";
        try{
            KeyFactory kf = KeyFactory.getInstance("RSA", "SunRsaSign");
            PKCS8EncodedKeySpec keySpec;
            byte[] keyBytes = Files.readAllBytes(Paths.get(path));
            String keyString = new String(keyBytes, StandardCharsets.UTF_8);
            if (keyString.contains(PKCS_1_PEM_HEADER)){
                keyString = keyString.replace(PKCS_1_PEM_HEADER, "").replace("\n", "");
                keyString = keyString.replace(PKCS_1_PEM_FOOTER, "").replace("\n", "");
                byte[] data = readPkcs1PrivateKey(Base64.getDecoder().decode(keyString));
                keySpec = new PKCS8EncodedKeySpec(data);
                privateKey = kf.generatePrivate(keySpec);
            }

        }catch (IOException e){
            e.printStackTrace();
        }catch (NoSuchAlgorithmException e){
            e.printStackTrace();
        }catch (NoSuchProviderException e){
            e.printStackTrace();
        }catch (InvalidKeySpecException e){

        }catch (GeneralSecurityException e){
            e.printStackTrace();
        }
        return privateKey;
    }


    private static byte[] readPkcs1PrivateKey(byte[] pkcs1Bytes){
        int pkcs1Length = pkcs1Bytes.length;
        int totalLength = pkcs1Length + 22;
        byte[] pkcs8Header = new byte[] {
                0x30, (byte) 0x82, (byte) ((totalLength >> 8) & 0xff), (byte) (totalLength & 0xff), // Sequence + total length
                0x2, 0x1, 0x0, // Integer (0)
                0x30, 0xD, 0x6, 0x9, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0xD, 0x1, 0x1, 0x1, 0x5, 0x0, // Sequence: 1.2.840.113549.1.1.1, NULL
                0x4, (byte) 0x82, (byte) ((pkcs1Length >> 8) & 0xff), (byte) (pkcs1Length & 0xff) // Octet string + length
        };
        byte[] pkcs8bytes = new byte[pkcs8Header.length + pkcs1Bytes.length];
        System.arraycopy(pkcs8Header, 0, pkcs8bytes, 0, pkcs8Header.length);
        System.arraycopy(pkcs1Bytes, 0, pkcs8bytes, pkcs8Header.length, pkcs1Bytes.length);
        return pkcs8bytes;
    }

}
