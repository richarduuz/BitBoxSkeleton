package unimelb.bitbox;


import com.sun.org.apache.xalan.internal.xsltc.runtime.InternalRuntimeError;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import unimelb.bitbox.ServerMain;
import unimelb.bitbox.ServerPart;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import javax.crypto.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;




/**
 * Created by ysy on 13/5/19.
 */
public class ClientMonitor extends Thread {
    private SecretKey sk;
    private String ThreadName;
    private Thread t;
    private Socket clientSocket;
    private  boolean flag = false;

    ClientMonitor(String name,Socket socket, SecretKey key)
    {
        ThreadName=name;
        clientSocket=socket;
        sk = key;
    }

    public void start()
    {
        if (t==null)
        {
            t=new Thread(this,ThreadName);
            t.start();
        }
    }

    public void run(){
        try
        {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
            String clientMsg;
            JSONParser parser = new JSONParser();
            while (true){
                if ((clientMsg=in.readLine())!=null){
                    System.out.println("receive the response from a client");
                    System.out.println("Client info: "+clientSocket.toString());
                    JSONObject socketPayload = (JSONObject) parser.parse(clientMsg);
                    // Bsse64 decode
                    String Base64Payload = (String)socketPayload.get("payload");
                    byte[] encryptPayload = Base64.getDecoder().decode(Base64Payload);
                    // Decrypt
                    Cipher cipher = Cipher.getInstance("AES");
                    cipher.init(Cipher.DECRYPT_MODE, sk);
                    JSONObject clientRequest = (JSONObject)parser.parse(new String(cipher.doFinal(encryptPayload), "UTF-8"));
                    client_process(clientRequest);
                    System.out.println(clientRequest.get("command"));
                    //TODO process the message from the client

                    //ServerPart server = new ServerPart("processing", socketRequest, ServerMain.fileSystemManager, clientSocket);
                    //server.process();
                }
                sleep(100);
            }

        }catch (ParseException e){
            //TODO process io exception
        }catch (IOException e){
            //TODO process parser exception
        }catch (InterruptedException e){
            //TODO process interrupt exception
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

    private void client_process(JSONObject js){
        try{
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF8"));
            String client_command = (String)js.get("command");
            JSONObject Response = new JSONObject();
            JSONObject payload = new JSONObject();
            switch (client_command){
                case ("LIST_PEERS_REQUEST"):
                {
                    System.out.println("Recieve list peers request from client");
                    ArrayList<String[]> connected_peer = ServerMain.connectedPeerInfo;
                    JSONArray peer = new JSONArray();
                    for (String[] peerInfo: connected_peer){
                        JSONObject tobeAdd = new JSONObject();
                        tobeAdd.put("host", peerInfo[0]);
                        tobeAdd.put("port", Long.parseLong(peerInfo[1]));
                        peer.add(tobeAdd);
                    }
                    Response.put("command", "LIST_PEER_RESPONSE");
                    Response.put("peers", peer);
                    payload.put("payload", generate_payload(Response, sk));
                    out.write(payload.toJSONString() + '\n');
                    out.flush();

                    // Once message sent, close the socket
                    clientSocket.close();
                    break;
                }
                case ("CONNECT_PEER_REQUEST"):
                {
                    String host = (String)js.get("host");
                    Long port = (long)js.get("port");
                    startConnecting new_connection = new startConnecting(host, port, "new_connection");
                    start();
                    Response.put("command", "CONNECT_PEER_RESPONSE");
                    Response.put("host", host);
                    Response.put("port", port);
                    for (String[] peer : ServerMain.connectedPeerInfo){
                        if (host.equals(peer[0])&& (port == Long.parseLong(peer[1]))){
                            flag = true;
                            Response.put("status", true);
                            Response.put("message", "connected to peer");
                        }
                    }
                    if (!flag){
                        Response.put("status", false);
                        Response.put("message", "connection fail");
                    }
                    payload.put("payload", generate_payload(Response, sk));
                    out.write(payload.toJSONString() + '\n');
                    out.flush();

                    // Once message sent, close the socket
                    clientSocket.close();
                    break;
                }
                case ("DISCONNECT_PEER_REQUEST"):
                {
                    String host = (String)js.get("host");
                    Long port = (long)js.get("port");
                    Response.put("command", "DISCONNECT_PEER_RESPONSE");
                    Response.put("host", host);
                    Response.put("port", port);
                    for (Socket key: ServerMain.peerSocket.keySet()){
                        if (ServerMain.peerSocket.get(key)[0].equals(host) && Long.parseLong(ServerMain.peerSocket.get(key)[1]) == port){
                            key.close();
                            flag = true;
                            Response.put("status", true);
                            Response.put("message", "disconnect from peer");
                        }
                    }
                    if (!flag){
                        Response.put("status", false);
                        Response.put("message", "connection not active");
                    }
                    payload.put("payload", generate_payload(Response, sk));
                    out.write(payload.toJSONString() + '\n');
                    out.flush();

                    clientSocket.close();



                    break;
                }
                default:
                {
                    Response.put("command", "Invalid request");
                    Response.put("message", "Invalid request");
                    payload.put("payload", generate_payload(Response, sk));
                    out.write(payload.toJSONString() + '\n');
                    out.flush();

                    clientSocket.close();
                }
            }

        }catch (IOException e){
            //TODO process io exception
        }

    }

    public String generate_payload(JSONObject js, SecretKey sk){
        String EncodedResponse = null;
        try{
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, sk);
            byte[] EncryptedResponse = cipher.doFinal(js.toJSONString().getBytes("UTF-8"));
            EncodedResponse = Base64.getEncoder().encodeToString(EncryptedResponse);
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
        }catch (UnsupportedEncodingException e){
            //TODO process unsupported encoding exception
        }
        return EncodedResponse;

    }
}
