package unimelb.bitbox;


import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.FileSystemManager;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Timer;

public class ResponseMonitor extends Thread {
    private String ThreadName;
    private DatagramSocket socket;
    private Thread t;
    private boolean isByteResponse = false;
    public boolean flag = false;
    private FileSystemManager.FileSystemEvent Event = null;
    private String handshake = null;


    public ResponseMonitor(String str, DatagramSocket s, FileSystemManager.FileSystemEvent event) {
        ThreadName = str;
        socket = s;
        Event = event;
    }

    public ResponseMonitor(String str, DatagramSocket s, boolean file_byte_request) {
        ThreadName = str;
        socket = s;
        isByteResponse = file_byte_request;

    }

    public ResponseMonitor(String name, DatagramSocket s, String str){
        ThreadName = name;
        socket = s;
        handshake = str;

    }

    public void start() {
        if (t == null) {
            t = new Thread(this, ThreadName);
            t.start();
        }
    }

    public void run() {
        try {
            String host = null;
            String port = null;
            while (true) {
                byte[] newbuffer = new byte[2*ServerMain.blockSize];
                DatagramPacket packet = new DatagramPacket(newbuffer, newbuffer.length);
                socket.receive(packet);
//                byte[] temp = packet.getData();
//                int len = 0;
//                for (int j = 0; j < temp.length; j++) {
//                    if (temp[j] != 0) {
//                        len += 1;
//                    }
//                }
//                byte[] response = new byte[len];
//                for (int i = 0; i < len; i++) {
//                    response[i] = temp[i];
//                }
                String response = new String(newbuffer, 0, packet.getLength());
                JSONObject peerResponse = new JSONObject();
                JSONParser parser = new JSONParser();
                String Expected_response = null;
                peerResponse = (JSONObject) parser.parse(response);
                System.out.println((String)peerResponse.get("command") + " on random port");



                // Incoming message is not FILE_BYTE_REQUEST
                if (handshake == null) {
                    if (!isByteResponse) {
                        if (!peerResponse.get("command").equals("FILE_BYTES_REQUEST")) {
                            switch (Event.event) {
                                case DIRECTORY_CREATE: {
                                    Expected_response = "DIRECTORY_CREATE_RESPONSE";
                                    break;
                                }
                                case DIRECTORY_DELETE: {
                                    Expected_response = "DIRECTORY_DELETE_RESPONSE";
                                    break;
                                }
                                case FILE_DELETE: {
                                    Expected_response = "FILE_DELETE_RESPONSE";
                                    break;
                                }
                                case FILE_CREATE: {
                                    Expected_response = "FILE_CREATE_RESPONSE";
                                    break;
                                }
                                case FILE_MODIFY: {
                                    Expected_response = "FILE_MODIFY_RESPONSE";
                                    break;
                                }
                            }
                            if (peerResponse.get("command").equals(Expected_response)) {
                                flag = true;
                                System.out.println(Expected_response + ": " + peerResponse.get("status"));
                                System.out.println(peerResponse.get("message"));
                            } else {
                                System.out.println("Invalid response");
                            }
                        } else {
                            System.out.println(peerResponse.get("command"));
                            System.out.println("Receiving file byte request");
                            processByteRequest(peerResponse, packet);
                        }

                    } else if (peerResponse.get("command").equals("FILE_BYTES_RESPONSE")) {
                        flag = true;
                        System.out.println("Receiving file byte response");
                        processByteResponse(peerResponse, packet);
                    } else if (peerResponse.get("command").equals("FILE_BYTES_REQUEST")) {
                        flag = true;
                        System.out.println("Receiving file byte request");
                        processByteRequest(peerResponse, packet);
                    } else {
                        System.out.println("Invalid message");
                    }
                }
                else {
                    if (peerResponse.get("command").equals("HANDSHAKE_RESPONSE")){
                        System.out.println("receiving handshake response");
                        flag = true;
                        JSONObject hostPort = (JSONObject)peerResponse.get("hostPort");
                        host = packet.getAddress().toString();
                        host = host.substring(host.lastIndexOf("/") + 1);
                        port = String.valueOf(hostPort.get("port"));
                        String[] peer = new String[]{host, port, (String)hostPort.get("host")};
                        boolean exist = false;
                        for (String[] peerInfo: ServerMain.onlinePeer){
                            if (peerInfo[0].equals(host)){
                                exist = true;
                            }
                        }
                        if (!exist){
                            ServerMain.onlinePeer.add(peer);
                            System.out.println("Adding peer host: " + peer[0] + ", port: " + peer[1]);
                            ArrayList<FileSystemManager.FileSystemEvent> tobesynced = ServerMain.fileSystemManager.generateSyncEvents();
                            for (FileSystemManager.FileSystemEvent event: tobesynced){
                                UDPClient syncedClient = new UDPClient("syncedClient", peer[0], Long.parseLong(peer[1]), event);
                                syncedClient.start();
                            }
                            Timer t = new Timer();
                            SyncAllPeer syncClient = new SyncAllPeer(host, port);
                            t.schedule(syncClient, 0, 60000);
                        }


                    }
                    else if (peerResponse.get("command").equals("CONNECTION_REFUSED")){
                        flag = true;
                    }
                }
                sleep(1000);
            }



        } catch (IOException e) {
            // TODO process IOException
            e.printStackTrace();

        } catch (ParseException e) {
            // TODO process ParserException
            e.printStackTrace();
        } catch (InterruptedException e) {
            //TODO process InterruptedException
            e.printStackTrace();
        }
    }

    public void processByteRequest(JSONObject peerResponse, DatagramPacket packet) {
        try {
            // get parthName
            String path_Name = (String) peerResponse.get("pathName");
            JSONObject description = (JSONObject) peerResponse.get("fileDescriptor");
            String md5 = (String) description.get("md5");
            long lastModified = (long) description.get("lastModified");
            long fileSize = (long) description.get("fileSize");

            JSONObject RESPONSE = new JSONObject();
            long position = (long) peerResponse.get("position");
            long length = (long) peerResponse.get("length");
            ByteBuffer readByte = ServerMain.fileSystemManager.readFile(md5, position, length);
            RESPONSE.put("command", "FILE_BYTES_RESPONSE");
            RESPONSE.put("fileDescriptor", description);
            RESPONSE.put("pathName", path_Name);
            RESPONSE.put("position", position);
            RESPONSE.put("length", length);
            RESPONSE.put("message", "successful read");
            RESPONSE.put("status", true);


            byte[] input = new byte[readByte.capacity()];
            for (int i = 0; i < input.length; i++)
                input[i] = readByte.get(i);

            String readByte_encode = Base64.getEncoder().encodeToString(input);
            RESPONSE.put("content", readByte_encode);
            RESPONSE.put("message", "successful read");
            RESPONSE.put("status", true);
            byte[] byteRequest = RESPONSE.toJSONString().getBytes("UTF-8");
            DatagramPacket byteResponse = new DatagramPacket(byteRequest, byteRequest.length, packet.getAddress(), packet.getPort());
            UDPClient udpClient = new UDPClient("additionClient", byteResponse, true);
            udpClient.start();
            System.out.println("Sending file bytes response");


        } catch (IOException e) {
            //TODO process IOException
        }catch (NoSuchAlgorithmException e){

        }
    }

    public void processByteResponse(JSONObject peerResponse, DatagramPacket packet){
        try{

            String path_Name = (String) peerResponse.get("pathName");
            JSONObject description = (JSONObject) peerResponse.get("fileDescriptor");
            String md5 = (String) description.get("md5");
            long lastModified = (long) description.get("lastModified");
            long fileSize = (long) description.get("fileSize");
            // There must be 5 file byte response
            if (!ServerMain.fileSystemManager.fileNameExists(path_Name, md5)){
                JSONObject RESPONSE = new JSONObject();
                long position = (long) peerResponse.get("position");
                long length = (long) peerResponse.get("length");
                String messages = (String) peerResponse.get("message");
                if (messages.equals("successful read")) {
                    byte[] brc = Base64.getDecoder().decode((String) peerResponse.get("content"));
                    ByteBuffer content_decoded = ByteBuffer.wrap(brc);
                    if (ServerMain.fileSystemManager.writeFile(path_Name, content_decoded, position)) {
                        // 大文件还是会出现md5不对的问题
                        if (!ServerMain.fileSystemManager.checkWriteComplete(path_Name)) {
                            // require file_bytes_request
                            RESPONSE.put("command", "FILE_BYTES_REQUEST");
                            RESPONSE.put("fileDescriptor", description);
                            RESPONSE.put("pathName", path_Name);
                            RESPONSE.put("position", position + length);
                            if ((int) (fileSize - (position + length)) > ServerMain.blockSize/2) {
                                RESPONSE.put("length", (long)ServerMain.blockSize/2);
                            } else {
                                RESPONSE.put("length", fileSize - position - length);
                            }
                            byte[] buffer = RESPONSE.toJSONString().getBytes("UTF-8");
                            DatagramPacket newRequest = new DatagramPacket(buffer, buffer.length, packet.getAddress(), packet.getPort());
                            UDPClient udpClient = new UDPClient("additionClient", newRequest, true);
                            udpClient.start();
                        }
                    }
            }


            }
            else{
                System.out.println("unsuccessful read");
            }


        } catch (IOException e) {
            //TODO process IOException
        }catch (NoSuchAlgorithmException e){

        }
    }
}
