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
            boolean exist = false;
            switch (client_command){
                case ("LIST_PEERS_REQUEST"):
                {
                    System.out.println("Recieve list peers request from client");
                    ArrayList<String[]> connected_peer = ServerMain.connectedPeerInfo;
                    JSONArray peer = new JSONArray();
                    if (ServerMain.mode.equals("tcp")){
                        for (Socket key: ServerMain.peerSocket.keySet()){
                            JSONObject tobeAdd = new JSONObject();
                            tobeAdd.put("host", ServerMain.peerSocket.get(key)[0]);
                            tobeAdd.put("port", Long.parseLong(ServerMain.peerSocket.get(key)[1]));
                            peer.add(tobeAdd);
                        }
                        Response.put("command", "LIST_PEERS_RESPONSE");
                        Response.put("peers", peer);
                    }
                    else{
                        for(String[] connectedPeer: ServerMain.onlinePeer){
                            JSONObject tobeAdd = new JSONObject();
                            tobeAdd.put("host", connectedPeer[0]);
                            tobeAdd.put("port", Long.parseLong(connectedPeer[1]));
                            peer.add(tobeAdd);
                        }
                        Response.put("command", "LIST_PEERS_RESPONSE");
                        Response.put("peers", peer);

                    }
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
                    new_connection.start();
                    Response.put("command", "CONNECT_PEER_RESPONSE");
                    Response.put("host", host);
                    Response.put("port", port);
                    sleep(1000);
                    if (ServerMain.mode.equals("tcp")){
                        for (Socket key: ServerMain.peerSocket.keySet()){
                            if (ServerMain.peerSocket.get(key)[0].equals(host) && Long.parseLong(ServerMain.peerSocket.get(key)[1]) == port){
                                exist = true;
                                Response.put("status", true);
                                Response.put("message", "connected to peer");
                            }
                        }
                        if (!exist){
                            Response.put("status", false);
                            Response.put("message", "connection fail");
                        }
                    }
                    else{
                        if (!exist){
                            UDPClient connecting = new UDPClient("connect", ServerMain.handshakePacket(host, port), "HANDSHAKE_REQUEST");
                            connecting.start();
                            sleep(1000);
                            for(String[] connectedPeer: ServerMain.onlinePeer ){
                                if ((connectedPeer[0].equals(host)|connectedPeer[2].equals(host))&&Long.parseLong(connectedPeer[1])==port){
                                    exist = true;
                                }
                            }
                        }
                        if (exist){
                            Response.put("status", true);
                            Response.put("message", "connected to peer");
                        }
                        else{
                            Response.put("status", false);
                            Response.put("message", "connection fail");
                        }


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
                    if (ServerMain.mode.equals("tcp")){
                        for (Socket key: ServerMain.peerSocket.keySet()){
                            if (ServerMain.peerSocket.get(key)[0].equals(host) && Long.parseLong(ServerMain.peerSocket.get(key)[1]) == port){
                                key.close();
                                exist = true;
                                Response.put("status", true);
                                Response.put("message", "disconnect from peer");
                            }
                        }
                        if (!exist){
                            Response.put("status", false);
                            Response.put("message", "connection not active");
                        }
                    }
                    else{
                        ArrayList<String> temp = new ArrayList<>();
                        for (int i = 0; i < ServerMain.onlinePeer.size(); i++){
                            if ((ServerMain.onlinePeer.get(i)[0].equals(host)|ServerMain.onlinePeer.get(i)[2].equals(host))&&Long.parseLong(ServerMain.onlinePeer.get(i)[1])==port){
                                temp.add(String.valueOf(i));
                                exist = true;
                            }
                        }
                        for (String str: temp){
                            ServerMain.onlinePeer.remove(ServerMain.onlinePeer.get(Integer.parseInt(str)));
                        }
                        temp.clear();
//                        for(String[] connectedPeer: ServerMain.onlinePeer ){
//                            if ((connectedPeer[0].equals(host)|connectedPeer[2].equals(host))&&Long.parseLong(connectedPeer[1])==port){
//                                ServerMain.onlinePeer.remove(connectedPeer);
//                                exist = true;
//                            }
//                        }
                        for (int i = 0; i < ServerMain.rememberPeer.size(); i++){
                            if ((ServerMain.rememberPeer.get(i)[0].equals(host)|ServerMain.rememberPeer.get(i)[2].equals(host))&&Long.parseLong(ServerMain.rememberPeer.get(i)[1])==port){
                                temp.add(String.valueOf(i));
                                exist = true;
                            }
                        }
                        for (String str: temp){
                            ServerMain.rememberPeer.remove(ServerMain.rememberPeer.get(Integer.parseInt(str)));
                        }
                        temp.clear();

//                        for (String[] remember: ServerMain.rememberPeer){
//                            if ((remember[0].equals(host)|remember[2].equals(host))&&Long.parseLong(remember[1])==port){
//                                ServerMain.rememberPeer.remove(remember);
//                                exist = true;
//                            }
//                        }

                        if (exist){
                            Response.put("status", true);
                            Response.put("message", "disconnect from peer");
                        }
                        else{
                            Response.put("status", false);
                            Response.put("message", "connection not active");
                        }

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
        }catch(InterruptedException e){

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
