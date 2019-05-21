package unimelb.bitbox;

import org.json.simple.JSONObject;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.testClient;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.ArrayList;

public class UDPClient extends Thread{
    private String host;
    private long port;
    private FileSystemManager.FileSystemEvent event;
    private String message = null;
    private String thread_name;
    private Thread t;
    private long blockSize;
    public ArrayList<FileSystemManager.FileSystemEvent> eventList = new ArrayList<>();

    public UDPClient(String name, String Host, long Port, long size, FileSystemManager.FileSystemEvent e){
        thread_name = name;
        host = Host;
        port = Port;
        event = e;
        blockSize = size;
        eventList.add(e);
    }

    public UDPClient(String name, String Host, long Port, long size, String Json_String){
        thread_name = name;
        host = Host;
        port = Port;
        message = Json_String;
        blockSize = size;
    }

    public void start(){
        if (t==null)
        {
            t=new Thread(this, thread_name);
            t.start();
        }
    }

    public void run(){
        try{
            DatagramSocket socket = new DatagramSocket();
            JSONObject request = new JSONObject();
            byte[] peerRequest;
            DatagramPacket packet = null;
            if (message == null){
                request = testClient.generateRequest(event);
                peerRequest = request.toJSONString().getBytes("UTF-8");

            }
            else {
                peerRequest = message.getBytes("UTF-8");
            }
            InetAddress address = InetAddress.getByName(host);
            packet =  new DatagramPacket(peerRequest, peerRequest.length, address, (int)port);
            socket.send(packet);
            System.out.println("Sending request to udp peer");
            byte [] responseBuffer = new byte[(int)blockSize];
            DatagramPacket response = new DatagramPacket(responseBuffer, responseBuffer.length);
            // create a monitor to check response
            ResponseMonitor monitor = new ResponseMonitor("monitor", response, socket, event);
            monitor.start();
            int retry = 0;
            while(retry <= ServerMain.maximumRetryNumbers){
                // timeout period : 6s (it should be 60s)
                sleep(6000);
                if (!monitor.flag){
                    System.out.println("no response");
                    socket.send(packet);
                    retry += 1;
                }

            }

        }catch (SocketException e){
            // TODO process Socket Exception
        }catch (UnsupportedEncodingException e){
            // TODO process UnsupportedEncodingException
        }catch (UnknownHostException e){
            // TODO process UnknownHostException
        }catch (IOException e){
            // TODO process IOException
        }catch (InterruptedException e){
            // TODO InterruptedException
        }

    }
}
