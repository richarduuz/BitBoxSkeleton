package unimelb.bitbox;

import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.json.simple.JSONObject;
import unimelb.bitbox.util.FileSystemManager;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.DatagramPacket;


public class Udp_Server extends Thread{
    private String thread_name;
    private DatagramSocket UDPsocket;
    private Thread T;
    private FileSystemManager fileSystemManager = ServerMain.fileSystemManager;
    private int packetSize;


    public Udp_Server(String name, DatagramSocket socket, int size){
        UDPsocket = socket;
        thread_name = name;
        packetSize = size;
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
            byte[] buffer = new byte[(int)packetSize];
            while(true){
                DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                System.out.println("Server is ready");
                UDPsocket.receive(request);
                System.out.println("read");
                JSONObject peerRequest = new JSONObject();
                JSONParser parser = new JSONParser();
                int len = 0;
                for (byte b: request.getData()){
                    if (b!= 0){
                        len += 1;
                    }
                }
                byte[] temp = new byte[len];
                for (int i = 0; i < len; i++){
                    temp[i] = request.getData()[i];
                }
                peerRequest = (JSONObject)parser.parse(new String(temp, "UTF-8"));
                String command = (String)peerRequest.get("command");
                DatagramPacket peerResponse;
                if (command.equals("DIRECTORY_CREATE_REQUEST") || command.equals("DIRECTORY_DELETE_REQUEST")){
                    String path_Name = (String)peerRequest.get("pathName");
                    switch (command){
                        case("DIRECTORY_CREATE_REQUEST"):{
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
                            byte[] packet = RESPONSE.toJSONString().getBytes("UTF-8");
                            peerResponse = new DatagramPacket(packet, packet.length, request.getAddress(), request.getPort());
                            UDPsocket.send(peerResponse);
                            System.out.println(request.getAddress() + ": " + request.getPort());
                        }
                        case ("DIRECTORY_DELETE_REQUEST"):{
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
                            byte[] packet = RESPONSE.toJSONString().getBytes("UTF-8");
                            peerResponse = new DatagramPacket(packet, packet.length, request.getAddress(), request.getPort());
                            UDPsocket.send(peerResponse);

                        }
                    }

                }

                sleep(1000);


            }


        }catch (IOException e){
            // TODO process IO Exception
        }catch (ParseException e){
            // TODO process Parse Exception
        }catch (InterruptedException E){

        }
    }


}
