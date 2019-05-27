package unimelb.bitbox;

import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.json.simple.JSONObject;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.FileSystemManager;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;


public class Udp_Server extends Thread{
    private String thread_name;
    private DatagramSocket UDPsocket;
    private Thread T;
    private FileSystemManager fileSystemManager = ServerMain.fileSystemManager;


    public Udp_Server(String name, DatagramSocket socket){
        UDPsocket = socket;
        thread_name = name;
    }

    public void start(){
        if (T==null)
        {
            T=new Thread(this, thread_name);
            T.start();
        }
    }

    public void run(){
        try{
            while(true){
                byte[] buffer = new byte[ServerMain.blockSize];
                DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                System.out.println("Server is ready");
                UDPsocket.receive(request);
                System.out.println("read");
                JSONObject peerRequest = new JSONObject();
                JSONParser parser = new JSONParser();
                String temp = new String(buffer, 0, request.getLength(), "UTF-8");
                peerRequest = (JSONObject)parser.parse(temp);




//                String[] pathName = ((String) peerRequest.get("pathName")).split("/");
//                String path = "";
//                String Name = pathName[pathName.length - 1];
//
//                if (pathName.length > 1){
//                    for (int i = 0; i < pathName.length - 1; i++) {
//                        if (i == 0) path = pathName[i] + "/";
//                        else if (i == pathName.length - 2) path += pathName[i];
//                        else path += pathName[i] + "/";
//                    }
//                }
//                String path_Name = "share/"+path+Name;

                String command = (String)peerRequest.get("command");
                System.out.println(command);
                if (command.equals("HANDSHAKE_REQUEST")){
                    JSONObject hostPort = (JSONObject)peerRequest.get("hostPort");
                    String host = request.getAddress().toString();
                    host = host.substring(host.lastIndexOf("/") + 1);
                    String port = String.valueOf(hostPort.get("port"));
                    boolean reachLimitedSize = ServerMain.rememberPeer.size() <= Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
                    boolean remenberedPeer = checkRememberPeer(request);
                    if (reachLimitedSize | remenberedPeer){
                        if (reachLimitedSize && !remenberedPeer){
                            String[] peerInfo = new String[]{host, port};
                            ServerMain.rememberPeer.add(peerInfo);

                        }

                        JSONObject response = new JSONObject();
                        response.put("command", "HANDSHAKE_RESPONSE");
                        JSONObject hostPortResponse = new JSONObject();
                        hostPortResponse.put("host", ServerMain.myhost);
                        hostPortResponse.put("port", ServerMain.udpPort);
                        response.put("hostPort", hostPortResponse);
                        byte[] handshakeResponse = response.toJSONString().getBytes("UTF-8");
                        DatagramPacket peerResponse = new DatagramPacket(handshakeResponse, handshakeResponse.length, request.getAddress(), request.getPort());
                        UDPsocket.send(peerResponse);

                        ArrayList<FileSystemManager.FileSystemEvent> tobesynced = ServerMain.fileSystemManager.generateSyncEvents();
                        for (FileSystemManager.FileSystemEvent event: tobesynced){
                            UDPClient syncedClient = new UDPClient("syncedClient", host, Long.parseLong(port), event);
                            syncedClient.start();
                        }
                    }

                }
                else if (checkOnlinePeer(request)) {

                    // get parthName
                    String path_Name = (String) peerRequest.get("pathName");
                    DatagramPacket peerResponse;
                    if (command.equals("DIRECTORY_CREATE_REQUEST") || command.equals("DIRECTORY_DELETE_REQUEST")) {
                        switch (command) {
                            case ("DIRECTORY_CREATE_REQUEST"): {
                                JSONObject RESPONSE = new JSONObject();
                                if (fileSystemManager.isSafePathName(path_Name) && !fileSystemManager.dirNameExists(path_Name)) {
                                    if (fileSystemManager.makeDirectory(path_Name)) {
                                        RESPONSE.put("message", "directory create successfully");
                                        RESPONSE.put("status", true);
                                    }
                                } else {
                                    RESPONSE.put("message", "fail to create directory");
                                    RESPONSE.put("status", false);
                                }
                                RESPONSE.put("command", "DIRECTORY_CREATE_RESPONSE");
                                RESPONSE.put("pathName", path_Name);
                                byte[] packet = RESPONSE.toJSONString().getBytes("UTF-8");
                                peerResponse = new DatagramPacket(packet, packet.length, request.getAddress(), request.getPort());
                                UDPsocket.send(peerResponse);
                                System.out.println(request.getAddress() + ": " + request.getPort());
                                break;
                            }
                            case ("DIRECTORY_DELETE_REQUEST"): {
                                JSONObject RESPONSE = new JSONObject();
                                if (fileSystemManager.isSafePathName(path_Name) && fileSystemManager.dirNameExists(path_Name)) {
                                    if (fileSystemManager.deleteDirectory(path_Name)) {
                                        RESPONSE.put("message", "directory deleted");
                                        RESPONSE.put("status", true);
                                    }
                                } else if (!fileSystemManager.isSafePathName(path_Name)) {
                                    RESPONSE.put("message", "unsafe pathname given");
                                    RESPONSE.put("status", false);
                                } else if (!fileSystemManager.dirNameExists(path_Name)) {
                                    RESPONSE.put("message", "pathname does not exist");
                                    RESPONSE.put("status", false);
                                } else {
                                    RESPONSE.put("message", "there was a problem deleting the directory");
                                    RESPONSE.put("status", false);
                                }
                                RESPONSE.put("command", "DIRECTORY_DELETE_RESPONSE");
                                RESPONSE.put("pathName", path_Name);
                                byte[] packet = RESPONSE.toJSONString().getBytes("UTF-8");
                                peerResponse = new DatagramPacket(packet, packet.length, request.getAddress(), request.getPort());
                                UDPsocket.send(peerResponse);
                                break;

                            }
                        }

                    }
                    if (command.equals("FILE_CREATE_REQUEST") || command.equals("FILE_DELETE_REQUEST") || command.equals("FILE_MODIFY_REQUEST")) {

                        FileSystemManager.FileDescriptor fileDescriptor;
                        JSONObject description = (JSONObject) peerRequest.get("fileDescriptor");
                        String md5 = (String) description.get("md5");
                        long lastModified = (long) description.get("lastModified");
                        long fileSize = (long) description.get("fileSize");
                        fileDescriptor = fileSystemManager.new FileDescriptor(lastModified, md5, fileSize);
                        switch (command) {

                            case ("FILE_MODIFY_REQUEST"): {
                                ///add new operation to the queue
                                JSONObject RESPONSE = new JSONObject();

                                if (fileSystemManager.isSafePathName(path_Name) && fileSystemManager.fileNameExists(path_Name)) {
                                    // Create file loader
                                    if (fileSystemManager.modifyFileLoader(path_Name, md5, lastModified)) {
                                        RESPONSE.put("command", "FILE_MODIFY_RESPONSE");
                                        RESPONSE.put("fileDescriptor", description);
                                        RESPONSE.put("pathName", path_Name);
                                        RESPONSE.put("message", "file loader ready");
                                        RESPONSE.put("status", true);

                                        byte[] packet = RESPONSE.toJSONString().getBytes("UTF-8");
                                        peerResponse = new DatagramPacket(packet, packet.length, request.getAddress(), request.getPort());
                                        UDPsocket.send(peerResponse);


                                        // When the file loader is ready, send file_bytes_request to get the modified file
                                        if (!fileSystemManager.checkShortcut(path_Name)) {
                                            JSONObject newRESPONSE = new JSONObject();
                                            newRESPONSE.put("command", "FILE_BYTES_REQUEST");
                                            newRESPONSE.put("fileDescriptor", description);
                                            newRESPONSE.put("pathName", path_Name);

                                            newRESPONSE.put("position", 0);
                                            if ((int) fileSize > Integer.parseInt(Configuration.getConfigurationValue("blockSize")))
                                                newRESPONSE.put("length", Long.parseLong(Configuration.getConfigurationValue("blockSize")));
                                            else
                                                newRESPONSE.put("length", fileSize);

                                            byte[] packet_byteRequest = newRESPONSE.toJSONString().getBytes("UTF-8");
                                            DatagramPacket newResponse = new DatagramPacket(packet_byteRequest, packet_byteRequest.length, request.getAddress(), request.getPort());
                                            UDPClient udpClient = new UDPClient("client", newResponse, true);
                                            udpClient.start();

                                        }
                                    }
                                } else {
                                    RESPONSE.put("command", "FILE_MODIFY_RESPONSE");
                                    RESPONSE.put("fileDescriptor", description);
                                    RESPONSE.put("pathName", path_Name);

                                    // The pathName is out the the share directory
                                    if (!fileSystemManager.isSafePathName(path_Name)) {
                                        RESPONSE.put("message", "unsafe pathname given");
                                        RESPONSE.put("status", false);
                                    }

                                    // matching to what the modification would have produced
                                    else if (fileSystemManager.modifyFileLoader(path_Name, md5, lastModified)) {
                                        RESPONSE.put("message", "file already exists with matching content");
                                        RESPONSE.put("status", false);
                                    }

                                    // pathname does not exist
                                    else if (!fileSystemManager.fileNameExists(path_Name)) {
                                        RESPONSE.put("message", "pathname does not exist");
                                        RESPONSE.put("status", false);
                                    }

                                    //Other problem
                                    else {
                                        RESPONSE.put("message", "there was a problem modifying the file");
                                        RESPONSE.put("status", false);
                                    }
                                    byte[] packet = RESPONSE.toJSONString().getBytes("UTF-8");
                                    peerResponse = new DatagramPacket(packet, packet.length, request.getAddress(), request.getPort());
                                    UDPsocket.send(peerResponse);

                                }
                                break;
                            }


                            case ("FILE_CREATE_REQUEST"): {
                                JSONObject RESPONSE = new JSONObject();
                                if (fileSystemManager.isSafePathName(path_Name) && !fileSystemManager.fileNameExists(path_Name)) {
                                    // Create file loader
                                    if (fileSystemManager.createFileLoader(path_Name, md5, fileSize, lastModified)) {
                                        RESPONSE.put("command", "FILE_CREATE_RESPONSE");
                                        RESPONSE.put("fileDescriptor", description);
                                        RESPONSE.put("pathName", path_Name);
                                        RESPONSE.put("message", "file loader ready");
                                        RESPONSE.put("status", true);

                                        byte[] packet = RESPONSE.toJSONString().getBytes("UTF-8");
                                        peerResponse = new DatagramPacket(packet, packet.length, request.getAddress(), request.getPort());
                                        UDPsocket.send(peerResponse);

                                        // Try to find and use local copy. If not, send fil_bytes_request.
                                        if (!fileSystemManager.checkShortcut(path_Name)) {
                                            JSONObject newRESPONSE = new JSONObject();
                                            newRESPONSE.put("command", "FILE_BYTES_REQUEST");
                                            newRESPONSE.put("fileDescriptor", description);
                                            newRESPONSE.put("pathName", path_Name);
                                            newRESPONSE.put("position", 0);
                                            if ((int) fileSize > ServerMain.blockSize)
                                                newRESPONSE.put("length", (long) ServerMain.blockSize);
                                            else
                                                newRESPONSE.put("length", fileSize);

                                            byte[] packet_byteRequest = newRESPONSE.toJSONString().getBytes("UTF-8");
                                            DatagramPacket newResponse = new DatagramPacket(packet_byteRequest, packet_byteRequest.length, request.getAddress(), request.getPort());
                                            UDPClient udpClient = new UDPClient("client", newResponse, true);
                                            udpClient.start();
                                        }

                                    }

                                } else if (fileSystemManager.fileNameExists(path_Name) && !fileSystemManager.fileNameExists(path_Name, md5)) {
                                    RESPONSE.put("command", "FILE_CREATE_RESPONSE");
                                    RESPONSE.put("fileDescriptor", description);
                                    RESPONSE.put("pathName", path_Name);
                                    RESPONSE.put("message", "pathname already exists");
                                    RESPONSE.put("status", false);

                                    byte[] packet = RESPONSE.toJSONString().getBytes("UTF-8");
                                    peerResponse = new DatagramPacket(packet, packet.length, request.getAddress(), request.getPort());
                                    UDPsocket.send(peerResponse);

                                    if (fileSystemManager.modifyFileLoader(path_Name, md5, lastModified)) {
                                        JSONObject MODIFY_RESPONSE = new JSONObject();
                                        MODIFY_RESPONSE.put("command", "FILE_MODIFY_RESPONSE");
                                        MODIFY_RESPONSE.put("fileDescriptor", description);
                                        MODIFY_RESPONSE.put("pathName", path_Name);
                                        MODIFY_RESPONSE.put("message", "file loader ready");
                                        MODIFY_RESPONSE.put("status", true);

                                        byte[] packet_modifyResponse = MODIFY_RESPONSE.toJSONString().getBytes("UTF-8");
                                        peerResponse = new DatagramPacket(packet_modifyResponse, packet_modifyResponse.length, request.getAddress(), request.getPort());
                                        UDPsocket.send(peerResponse);

                                        if (!fileSystemManager.checkShortcut(path_Name)) {
                                            JSONObject newRESPONSE = new JSONObject();
                                            newRESPONSE.put("command", "FILE_BYTES_REQUEST");
                                            newRESPONSE.put("fileDescriptor", description);
                                            newRESPONSE.put("pathName", path_Name);
                                            newRESPONSE.put("position", 0);
                                            if ((int) fileSize > ServerMain.blockSize)
                                                newRESPONSE.put("length", (long) ServerMain.blockSize);
                                            else
                                                newRESPONSE.put("length", fileSize);


                                            byte[] packet_byteRequest = newRESPONSE.toJSONString().getBytes("UTF-8");
                                            DatagramPacket newResponse = new DatagramPacket(packet_byteRequest, packet_byteRequest.length, request.getAddress(), request.getPort());
                                            // create a udp client to deal with file_byte_request
                                            UDPClient udpClient = new UDPClient("client", newResponse, true);
                                            udpClient.start();

                                        }
                                    }
                                } else {
                                    RESPONSE.put("command", "FILE_CREATE_RESPONSE");
                                    RESPONSE.put("fileDescriptor", description);
                                    RESPONSE.put("pathName", path_Name);

                                    // The pathName is out the the share directory
                                    if (fileSystemManager.isSafePathName(path_Name)) {
                                        RESPONSE.put("message", "unsafe pathname given");
                                        RESPONSE.put("status", false);
                                    }

                                    // The file already exists.
                                    else if (fileSystemManager.fileNameExists(path_Name, md5)) {
                                        RESPONSE.put("message", "pathname already exists");
                                        RESPONSE.put("status", false);
                                    }

                                    //Other problem
                                    else {
                                        RESPONSE.put("message", "there was a problem creating the file");
                                        RESPONSE.put("status", false);
                                    }
                                    byte[] packet_file_create_response = RESPONSE.toJSONString().getBytes("UTF-8");
                                    peerResponse = new DatagramPacket(packet_file_create_response, packet_file_create_response.length, request.getAddress(), request.getPort());
                                    UDPsocket.send(peerResponse);
                                }
                                break;
                            }
                            case ("FILE_BYTES_REQUEST"): {
                                JSONObject RESPONSE = new JSONObject();
                                long position = (long) peerRequest.get("position");
                                long length = (long) peerRequest.get("length");
                                String messages = (String) peerRequest.get("message");
                                ByteBuffer readByte = fileSystemManager.readFile(md5, position, length);
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
                                byte[] packet = RESPONSE.toJSONString().getBytes("UTF-8");
                                peerResponse = new DatagramPacket(packet, packet.length, request.getAddress(), request.getPort());
                                UDPClient udpClient = new UDPClient("additionClient", peerResponse, true);
                                udpClient.start();
                                break;

                            }
                            case ("FILE_BYTES_RESPONSE"): {
                                JSONObject RESPONSE = new JSONObject();
                                long position = (long) peerRequest.get("position");
                                long length = (long) peerRequest.get("length");
                                String messages = (String) peerRequest.get("message");
                                if (messages.equals("successful read")) {

                                    byte[] brc = Base64.getDecoder().decode((String) peerRequest.get("content"));
                                    ByteBuffer content_decoded = ByteBuffer.wrap(brc);
                                    //content_decoded.position(brc.length);
                                    if (fileSystemManager.writeFile(path_Name, content_decoded, position)) {
                                        // 大文件还是会出现md5不对的问题
                                        if (!fileSystemManager.checkWriteComplete(path_Name)) {
                                            RESPONSE.put("command", "FILE_BYTES_REQUEST");
                                            RESPONSE.put("fileDescriptor", description);
                                            RESPONSE.put("pathName", path_Name);
                                            RESPONSE.put("position", position + length);
                                            if ((int) (fileSize - (position + length)) > ServerMain.blockSize) {
                                                RESPONSE.put("length", ServerMain.blockSize);
                                            } else {
                                                RESPONSE.put("length", fileSize - position - length);
                                            }

                                            byte[] packet = RESPONSE.toJSONString().getBytes("UTF-8");
                                            peerResponse = new DatagramPacket(packet, packet.length, request.getAddress(), request.getPort());
                                            UDPClient udpClient = new UDPClient("additionClient", peerResponse, true);
                                            udpClient.start();

                                        }

                                    }
                                }
                                break;
                            }
                            case ("FILE_DELETE_REQUEST"): {
                                JSONObject RESPONSE = new JSONObject();
                                RESPONSE.put("command", "FILE_DELETE_RESPONSE");
                                RESPONSE.put("fileDescriptor", description);
                                RESPONSE.put("pathName", path_Name);
                                if (fileSystemManager.isSafePathName(path_Name) && fileSystemManager.fileNameExists(path_Name)) {
                                    if (fileSystemManager.deleteFile(path_Name, lastModified, md5)) {

                                        RESPONSE.put("message", "file loader ready");
                                        RESPONSE.put("status", true);
                                    }
                                } else if (!fileSystemManager.isSafePathName(path_Name)) {
                                    RESPONSE.put("message", "unsafe pathname given");
                                    RESPONSE.put("status", false);
                                } else if (!fileSystemManager.fileNameExists(path_Name)) {
                                    RESPONSE.put("message", "pathname not exists");
                                    RESPONSE.put("status", false);
                                }
                                byte[] packet = RESPONSE.toJSONString().getBytes("UTF-8");
                                peerResponse = new DatagramPacket(packet, packet.length, request.getAddress(), request.getPort());
                                UDPsocket.send(peerResponse);
                                break;
                            }
                        }

                    }
                }

                sleep(1000);


            }


        }catch (IOException e){
            // TODO process IO Exception
            e.printStackTrace();
        }catch (ParseException e){
            // TODO process Parse Exception
            e.printStackTrace();
        }catch (InterruptedException E){
            E.printStackTrace();

        }catch (NoSuchAlgorithmException e){
            // TODO process NOSuchAlgorithm Exception
        }



    }

    private boolean checkRememberPeer(DatagramPacket packet){

        String host = packet.getAddress().toString();
        host = host.substring(host.lastIndexOf("/") + 1);
        for (String[] peer: ServerMain.rememberPeer){
            if (peer[0].equals(host)){
                return true;
            }
        }
        return false;
    }

    private boolean checkOnlinePeer(DatagramPacket packet){

        String host = packet.getAddress().toString();
        host = host.substring(host.lastIndexOf("/") + 1);
        for (String[] peer: ServerMain.onlinePeer){
            if (peer[0].equals(host)){
                return true;
            }
        }
        return false;
    }


}
