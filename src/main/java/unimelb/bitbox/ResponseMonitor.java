package unimelb.bitbox;


import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import unimelb.bitbox.util.FileSystemManager;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ResponseMonitor extends Thread{
    private String ThreadName;
    private DatagramPacket packet;
    private DatagramSocket socket;
    private Thread t;
    public boolean flag = false;
    private FileSystemManager.FileSystemEvent Event;
    private String byteRequest = null;

    public ResponseMonitor(String str, DatagramPacket p, DatagramSocket s, FileSystemManager.FileSystemEvent event){
        ThreadName = str;
        packet = p;
        socket = s;
        Event = event;
    }

    public ResponseMonitor(String str, DatagramPacket p, DatagramSocket s, String file_byte_request){
        ThreadName = str;
        packet = p;
        socket = s;
        byteRequest = file_byte_request;

    }

    public void start(){
        if (t==null)
        {
            t=new Thread(this, ThreadName);
            t.start();
        }
    }

    public void run(){
        try{
            socket.receive(packet);
            byte[] temp = packet.getData();
            int len = 0;
            for (byte b: temp){
                if (b != 0){
                    len += 1;
                }
            }
            byte[] response = new byte[len];
            for (int i = 0; i < len; i++){
                response[i] = temp[i];
            }
            JSONObject peerResponse = new JSONObject();
            JSONParser parser = new JSONParser();
            String Expected_response = null;
            // Incoming message is not FILE_BYTE_REQUEST
            if (byteRequest == null){
                switch(Event.event){
                    case DIRECTORY_CREATE:{
                        Expected_response = "DIRECTORY_CREATE_RESPONSE";
                        break;
                    }
                    case DIRECTORY_DELETE:{
                        Expected_response = "DIRECTORY_DELETE_RESPONSE";
                        break;
                    }
                    case FILE_DELETE:{
                        Expected_response = "FILE_CREATE_RESPONSE";
                        break;
                    }
                    case FILE_CREATE:{
                        Expected_response = "FILE_CREATE_RESPONSE";
                        break;
                    }
                    case FILE_MODIFY:{
                        Expected_response = "FILE_MODIFY_RESPONSE";
                        break;
                    }
                }
            }
            else{
                Expected_response = "FILE_BYTE_RESPONSE";
            }
            peerResponse = (JSONObject)parser.parse(new String(response, "UTF-8"));
            if (peerResponse.get("command").equals(Expected_response)){
                flag = true;
                System.out.println(Expected_response + " " + peerResponse.get("status"));
            }
            else{
                System.out.println("Invalid response");
            }

        }catch (IOException E){
            // TODO process IOException

        }catch (ParseException e){
            // TODO process ParserException
        }
    }
}
