package unimelb.bitbox;

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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;

public class bitbox_client {

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
                peer_port = Long.parseLong(peer.split(":")[1]);
            }
            String server_host = server.split(":")[0];
            long server_port = Long.parseLong(server.split(":")[1]);
            Socket clientSocket = new Socket(server_host, (int)server_port);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"));
            JSONObject auth_request = new JSONObject();
            auth_request.put("command", "AUTH_REQUEST");
            auth_request.put("identity", "taiq@student.unimelb.edu.au");
            out.write(auth_request.toJSONString() + '\n');
            out.flush();

            String serverMsg = in.readLine();
            JSONParser JsParser = new JSONParser();
            JSONObject auth_response = (JSONObject)JsParser.parse(serverMsg);
            System.out.println(auth_response.get("command") + ": " + auth_response.get("status"));
            if ((boolean)auth_response.get("status")){
                String Base64AES = (String)auth_response.get("AES128");
                byte[] temp = Base64.getDecoder().decode(Base64AES);
                SecretKeySpec sk = new SecretKeySpec(temp, "AES");
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
                System.out.println(serverResponse.get("message"));

            }





        }catch (CmdLineException e){
            // TODO process CmdLine exception
        }catch (UnknownHostException e){
            // TODO process unknown host exception
        }catch (IOException e){
            // TODO process IO exception
        }catch (ParseException e){
            // TODO process parser exception
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

}
