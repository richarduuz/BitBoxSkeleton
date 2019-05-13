package unimelb.bitbox;

import org.json.simple.JSONObject;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.FileSystemManager;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;


public class ServerPart{
    private JSONObject socketRequest;
    private String ThreadName;
    private FileSystemManager fileSystemManager;
    private Socket peerSocket;

    public ServerPart(String name, JSONObject request, FileSystemManager manager, Socket socket)
    {
        socketRequest=request;
        ThreadName=name;
        fileSystemManager=manager;
        peerSocket=socket;
    }


    public void process()
    {

        {
            ServerSocket listeningSocket = null;
            int port = Integer.parseInt(Configuration.getConfigurationValue("port"));

            Socket clientSocket = null;

            try {
                    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(peerSocket.getOutputStream(), "UTF-8"));
                    JSONObject Request = socketRequest;
                    String ClientRequest = (String) Request.get("command");
                    System.out.println("client request here "+ClientRequest);
                    JSONObject clientRequest = socketRequest;

                    String[] command = ((String) clientRequest.get("command")).split("_");
                    String Command = command[0] + "_" + command[1] + "_" + command[2];


                    String[] pathName = ((String) clientRequest.get("pathName")).split("/");
                    String path = "";
                    String Name = pathName[pathName.length - 1];

                    if (pathName.length > 1){
                        for (int i = 0; i < pathName.length - 1; i++) {
                            if (i == 0) path = pathName[i] + "/";
                            else if (i == pathName.length - 2) path += pathName[i];
                            else path += pathName[i] + "/";
                        }
                    }


                    String path_Name = path + Name;
                    String temp = path;
                    path = "share/" + path;
                    FileSystemManager.FileSystemEvent fileSystemEvent;
                    try {
                        // FIlE CREATE AND DELETE
                        if (Command.equals("FILE_CREATE_REQUEST") | Command.equals("FILE_DELETE_REQUEST") | Command.equals("FILE_BYTES_RESPONSE")|Command.equals("FILE_BYTES_REQUEST")|Command.equals("FILE_MODIFY_REQUEST")) {
                            FileSystemManager.FileDescriptor fileDescriptor;
                            JSONObject description = (JSONObject) clientRequest.get("fileDescriptor");
                            String md5 = (String) description.get("md5");
                            long lastModified = (long) description.get("lastModified");
                            long fileSize = (long) description.get("fileSize");
                            fileDescriptor = fileSystemManager.new FileDescriptor(lastModified, md5, fileSize);
                            switch (Command) {
                                case ("FILE_CREATE_RESPONSE"): break;
                                case ("FILE_MODIFY_REQUEST"): {
                                    ///add new operation to the queue
                                    JSONObject RESPONSE=new JSONObject();

                                    if(fileSystemManager.isSafePathName(path_Name) && fileSystemManager.fileNameExists(path_Name)) {
                                        // Create file loader
                                        if (fileSystemManager.modifyFileLoader(path_Name, md5, lastModified)) {
                                            RESPONSE.put("command", "FILE_MODIFY_RESPONSE");
                                            RESPONSE.put("fileDescriptor", description);
                                            RESPONSE.put("pathName", path_Name);
                                            RESPONSE.put("message", "file loader ready");
                                            RESPONSE.put("status", true);
                                            out.write(RESPONSE.toJSONString() + '\n');

                                            // When the file loader is ready, send file_bytes_request to get the modified file
                                            if (!fileSystemManager.checkShortcut(path_Name)){
                                                JSONObject newRESPONSE = new JSONObject();
                                                newRESPONSE.put("command", "FILE_BYTES_REQUEST");
                                                newRESPONSE.put("fileDescriptor", description);
                                                newRESPONSE.put("pathName", path_Name);

                                                newRESPONSE.put("position", 0);
                                                if ((int)fileSize>Integer.parseInt(Configuration.getConfigurationValue("blockSize")))
                                                    newRESPONSE.put("length", Long.parseLong(Configuration.getConfigurationValue("blockSize")));
                                                else
                                                    newRESPONSE.put("length",fileSize);

                                                out.write(newRESPONSE.toJSONString() + '\n');
                                                out.flush();

                                            }
                                        }
                                    }
                                    else{
                                        RESPONSE.put("command","FILE_MODIFY_RESPONSE");
                                        RESPONSE.put("fileDescriptor", description);
                                        RESPONSE.put("pathName", path_Name);

                                        // The pathName is out the the share directory
                                        if (!fileSystemManager.isSafePathName(path_Name)){
                                            RESPONSE.put("message", "unsafe pathname given");
                                            RESPONSE.put("status", false);
                                        }

                                        // matching to what the modification would have produced
                                        else if (fileSystemManager.modifyFileLoader(path_Name, md5, lastModified)){
                                            RESPONSE.put("message", "file already exists with matching content");
                                            RESPONSE.put("status", false);
                                        }

                                        // pathname does not exist
                                        else if (!fileSystemManager.fileNameExists(path_Name)){
                                            RESPONSE.put("message", "pathname does not exist");
                                            RESPONSE.put("status", false);
                                        }

                                        //Other problem
                                        else{
                                            RESPONSE.put("message", "there was a problem modifying the file");
                                            RESPONSE.put("status", false);
                                        }
                                        out.write(RESPONSE.toJSONString()+'\n');
                                        out.flush();
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

                                            out.write(RESPONSE.toJSONString() + '\n');
                                            out.flush();

                                            // Try to find and use local copy. If not, send fil_bytes_request.
                                            if (!fileSystemManager.checkShortcut(path_Name)) {
                                                JSONObject newRESPONSE = new JSONObject();
                                                newRESPONSE.put("command", "FILE_BYTES_REQUEST");
                                                newRESPONSE.put("fileDescriptor", description);
                                                newRESPONSE.put("pathName", path_Name);
                                                newRESPONSE.put("position", 0);
                                                if ((int)fileSize>Integer.parseInt(Configuration.getConfigurationValue("blockSize")))
                                                    newRESPONSE.put("length", Long.parseLong(Configuration.getConfigurationValue("blockSize")));
                                                else
                                                    newRESPONSE.put("length",fileSize);
                                                out.write(newRESPONSE.toJSONString() + '\n');
                                                out.flush();
                                            }

                                        }

                                    }
                                    else if (fileSystemManager.fileNameExists(path_Name) && !fileSystemManager.fileNameExists(path_Name, md5)){
                                        RESPONSE.put("command", "FILE_CREATE_RESPONSE");
                                        RESPONSE.put("fileDescriptor", description);
                                        RESPONSE.put("pathName", path_Name);
                                        RESPONSE.put("message", "pathname already exists");
                                        RESPONSE.put("status", false);
                                        out.write(RESPONSE.toJSONString() + '\n');
                                        out.flush();

                                        if (fileSystemManager.modifyFileLoader(path_Name, md5, lastModified)){
                                            JSONObject MODIFY_RESPONSE = new JSONObject();
                                            MODIFY_RESPONSE.put("command", "FILE_MODIFY_RESPONSE");
                                            MODIFY_RESPONSE.put("fileDescriptor", description);
                                            MODIFY_RESPONSE.put("pathName", path_Name);
                                            MODIFY_RESPONSE.put("message", "file loader ready");
                                            MODIFY_RESPONSE.put("status", true);
                                            out.write(MODIFY_RESPONSE.toJSONString() + '\n');
                                            out.flush();

                                            if (!fileSystemManager.checkShortcut(path_Name)){
                                                JSONObject newRESPONSE = new JSONObject();
                                                newRESPONSE.put("command", "FILE_BYTES_REQUEST");
                                                newRESPONSE.put("fileDescriptor", description);
                                                newRESPONSE.put("pathName", path_Name);
                                                newRESPONSE.put("position", 0);
                                                if ((int)fileSize>Integer.parseInt(Configuration.getConfigurationValue("blockSize")))
                                                    newRESPONSE.put("length", Long.parseLong(Configuration.getConfigurationValue("blockSize")));
                                                else
                                                    newRESPONSE.put("length",fileSize);
                                                out.write(newRESPONSE.toJSONString() + '\n');
                                                out.flush();
                                            }
                                        }
                                    }
                                    else {
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
                                        out.write(RESPONSE.toJSONString() + '\n');
                                        out.flush();
                                    }
                                    break;
                                }


                                // FILE_BYTES_REQUEST
                                case("FILE_BYTES_REQUEST"):{
                                    JSONObject RESPONSE = new JSONObject();
                                    long position = (long) clientRequest.get("position");
                                    long length = (long)clientRequest.get("length");
                                    String messages = (String) clientRequest.get("message");
                                    ByteBuffer readByte = fileSystemManager.readFile(md5, position, length);
                                    RESPONSE.put("command", "FILE_BYTES_RESPONSE");
                                    RESPONSE.put("fileDescriptor", description);
                                    RESPONSE.put("pathName", path_Name);
                                    RESPONSE.put("position", position);
                                    RESPONSE.put("length", length);
                                    RESPONSE.put("message", "successful read");
                                    RESPONSE.put("status",true);


                                    byte[] input=new byte[readByte.capacity()];
                                    for (int i=0;i<input.length;i++)
                                        input[i]=readByte.get(i);

                                    String readByte_encode= Base64.getEncoder().encodeToString(input);
                                    RESPONSE.put("content", readByte_encode);
                                    RESPONSE.put("message", "successful read");
                                    RESPONSE.put("status", true);

                                    out.write(RESPONSE.toJSONString() + '\n');
                                    out.flush();


                                    break;
                                }



                                // Recieving file_bytes_response
                                case ("FILE_BYTES_RESPONSE"): {
                                    JSONObject RESPONSE = new JSONObject();
                                    long position = (long) clientRequest.get("position");
                                    long length = (long)clientRequest.get("length");
                                    String messages = (String) clientRequest.get("message");
                                    if (messages.equals("successful read")) {

                                        byte[] brc=Base64.getDecoder().decode((String)clientRequest.get("content"));
                                        ByteBuffer content_decoded=ByteBuffer.wrap(brc);
                                        //content_decoded.position(brc.length);
                                        if (fileSystemManager.writeFile(path_Name, content_decoded, position)) {
                                            // 大文件还是会出现md5不对的问题
                                            if (!fileSystemManager.checkWriteComplete(path_Name)) {
                                                RESPONSE.put("command", "FILE_BYTES_REQUEST");
                                                RESPONSE.put("fileDescriptor", description);
                                                RESPONSE.put("pathName", path_Name);
                                                RESPONSE.put("position", position + length);
                                                if ((int)(fileSize - (position + length)) > Integer.parseInt(Configuration.getConfigurationValue("blockSize"))){
                                                    RESPONSE.put("length", Long.parseLong(Configuration.getConfigurationValue("blockSize")));
                                                }
                                                else{
                                                    RESPONSE.put("length", fileSize - position - length);
                                                }
                                                out.write(RESPONSE.toJSONString() + '\n');
                                                out.flush();
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
                                    out.write(RESPONSE.toJSONString() + '\n');
                                    out.flush();
                                    break;
                                }
                            }

                        }
                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    }

                    switch (Command) {
                        case ("DIRECTORY_CREATE_REQUEST"): {
                            JSONObject RESPONSE = new JSONObject();
                            if (fileSystemManager.isSafePathName(path_Name) && !fileSystemManager.dirNameExists(path_Name)) {

                                if (fileSystemManager.makeDirectory(path_Name))
                                {
                                    RESPONSE.put("message", "directory create successfully");
                                    RESPONSE.put("status", true);
                                }
                            }
                            else {
                                RESPONSE.put("message", "fail to create directory");
                                RESPONSE.put("status", false);
                            }
                            RESPONSE.put("command", "DIRECTORY_CREATE_RESPONSE");
                            RESPONSE.put("pathName", path_Name);
                            out.write(RESPONSE.toJSONString() + '\n');
                            out.flush();
                            break;
                        }
                        case ("DIRECTORY_DELETE_REQUEST"): {
                            JSONObject RESPONSE = new JSONObject();
                            if (fileSystemManager.isSafePathName(path_Name) && fileSystemManager.dirNameExists(path_Name)) {
                                if (fileSystemManager.deleteDirectory(path_Name)) {
                                    RESPONSE.put("message", "directory deleted");
                                    RESPONSE.put("status", true);
                                }
                            } else if(!fileSystemManager.isSafePathName(path_Name)){
                                RESPONSE.put("message", "unsafe pathname given");
                                RESPONSE.put("status", false);
                            }else if(!fileSystemManager.dirNameExists(path_Name)){
                                RESPONSE.put("message", "pathname does not exist");
                                RESPONSE.put("status", false);
                            }
                            else {
                                RESPONSE.put("message", "there was a problem deleting the directory");
                                RESPONSE.put("status", false);
                            }
                            RESPONSE.put("command", "DIRECTORY_DELETE_RESPONSE");
                            RESPONSE.put("pathName", path_Name);
                            out.write(RESPONSE.toJSONString() + '\n');
                            out.flush();
                            break;
                        }
                    }
                    System.out.println("Response sent");

            } catch (SocketException ex) {
                ex.printStackTrace();
            }catch (IOException e) {
                e.printStackTrace();
            }
            finally {
                if(listeningSocket != null) {
                    try {
                        listeningSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
