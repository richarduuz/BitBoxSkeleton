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
    private boolean IsByteRequest = false;
    private String thread_name;
    private Thread t;
    private DatagramPacket bytePacket;

    public UDPClient(String name, String Host, long Port, FileSystemManager.FileSystemEvent e){
        thread_name = name;
        host = Host;
        port = Port;
        event = e;
    }

    public UDPClient(String name, DatagramPacket datagram, boolean byteRequest){
        thread_name = name;
        IsByteRequest = byteRequest;
        bytePacket = datagram;
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
            if (!IsByteRequest){
                request = testClient.generateRequest(event);
                peerRequest = request.toJSONString().getBytes("UTF-8");
                InetAddress address = InetAddress.getByName(host);
                packet =  new DatagramPacket(peerRequest, peerRequest.length, address, (int)port);
                socket.send(packet);
                System.out.println("Sending request to udp peer");

            }
            else {
                socket.send(bytePacket);
            }


            // create a monitor to check response
            ResponseMonitor monitor;
            if (!IsByteRequest){
                monitor = new ResponseMonitor("monitor",  socket, event);
                monitor.start();
            }
            else{
                monitor = new ResponseMonitor("monitor",  socket, true);
                monitor.start();

            }


            int retry = 0;
            while(retry <= ServerMain.maximumRetryNumbers){
                // timeout period : 6s (it should be 60s)
                sleep(60000);
                if (!monitor.flag){
                    System.out.println("no response");
                    if (IsByteRequest){
                        socket.send(bytePacket);
                    }
                    else{
                        socket.send(packet);
                    }
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
