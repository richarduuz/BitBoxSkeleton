package unimelb.bitbox;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.testClient;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;


public class acceptConnection extends Thread{
    private ServerSocket socket;
    private String ThreadName;
    private Thread t;
    private BufferedReader in;
    private Socket clientSocket;

    public acceptConnection(String name,ServerSocket serverSocket)
    {
        ThreadName=name;
        socket=serverSocket;
    }

    public void start()
    {
        if (t==null)
        {
            t=new Thread(this,ThreadName);
            t.start();
        }
    }

    public void run() {
        try {
            while(true) {
                int port = Integer.parseInt(Configuration.getConfigurationValue("port"));
                JSONParser parser = new JSONParser();
                clientSocket = this.socket.accept();
                if (ServerMain.peerSocket.get(clientSocket)==null)
                {
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"));
                String clientMsg = null;
                clientMsg = in.readLine();
                JSONObject Request = (JSONObject) parser.parse(clientMsg);
                JSONObject HANDSHAKE_RESPONSE = new JSONObject();
                String ClientRequest = (String) Request.get("command");
                JSONObject wantToConnectPeer=(JSONObject) Request.get("hostPort");
                String wantToConnectPeerHost= (String) wantToConnectPeer.get("host");
                String wantToConnectPeerPort= String.valueOf((long) wantToConnectPeer.get("port"));
                System.out.println("reveiving a connection request");
                System.out.println("request content: " + ClientRequest + "\n");
                if (!ClientRequest.equals("HANDSHAKE_REQUEST")) {
                    HANDSHAKE_RESPONSE.put("command", "INVALID_PROTOCOL");
                    HANDSHAKE_RESPONSE.put("message", "message must contain a command field as string");
                    out.write(HANDSHAKE_RESPONSE.toJSONString() + '\n');
                    out.flush();
                    clientSocket.close();
                } else if (ClientRequest.equals("HANDSHAKE_REQUEST") && ServerMain.peerSocket.size()+1 > ServerMain.maximumIncommingConnections) {
                    JSONArray peers = new JSONArray();
                    for (String[] peerInfo : ServerMain.connectedPeerInfo) {
                        JSONObject tobeadded = new JSONObject();
                        tobeadded.put("host", peerInfo[0]);
                        tobeadded.put("port", Long.parseLong(peerInfo[1]));
                        peers.add(tobeadded);
                    }
                    HANDSHAKE_RESPONSE.put("command", "CONNECTION_REFUSED");
                    HANDSHAKE_RESPONSE.put("message", "connection limit reached");
                    HANDSHAKE_RESPONSE.put("peers", peers);
                    out.write(HANDSHAKE_RESPONSE.toJSONString() + '\n');
                    out.flush();
                    clientSocket.close();
                } else {
                    JSONObject hostPort = new JSONObject();
                    hostPort.put("host", clientSocket.getLocalAddress().getHostName());
                    hostPort.put("port", port);
                    HANDSHAKE_RESPONSE.put("command", "HANDSHAKE_RESPONSE");
                    HANDSHAKE_RESPONSE.put("hostPort", hostPort);
                    out.write(HANDSHAKE_RESPONSE.toJSONString() + '\n');
                    out.flush();
                    ArrayList<FileSystemManager.FileSystemEvent> tobesynced = ServerMain.fileSystemManager.generateSyncEvents();
                    peerSocketMonitor socketMonitor = new peerSocketMonitor("socket", clientSocket);
                    //TODO to do sync
                    socketMonitor.start();
                    sleep(1000);
                    for (FileSystemManager.FileSystemEvent event : tobesynced) {
                        System.out.println("events to be synced " + event.toString());
                        testClient client = new testClient("client" + clientSocket.toString(), clientSocket, ServerMain.fileSystemManager, event);
                        client.start();
                    }
                    ServerMain.peerSocket.put(clientSocket, new String[]{wantToConnectPeerHost,wantToConnectPeerPort});
                    ServerMain.connectedPeerInfo.add(new String[]{wantToConnectPeerHost,wantToConnectPeerPort});
                    System.out.println("socket has been added and alert has been set" + "\n");
                }
            }
            else
                {
                    clientSocket.close();
                    System.out.println("peer has already been connected");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }catch(ParseException e){
            e.printStackTrace();
        }catch (NoSuchAlgorithmException e){
            e.printStackTrace();
        }catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
