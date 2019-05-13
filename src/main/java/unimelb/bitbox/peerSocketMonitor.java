package unimelb.bitbox;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import unimelb.bitbox.util.FileSystemManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;


public class peerSocketMonitor extends Thread {
    private String ThreadName;
    private Thread t;
    private Socket peer;
    private FileSystemManager fileSystemManager= ServerMain.fileSystemManager;

    public peerSocketMonitor(String name, Socket socket)
    {
        peer=socket;
        ThreadName=name;
    }

    public void start()
    {
        if (t==null)
        {
            t=new Thread(this,ThreadName);
            t.start();
        }
    }

    public void run()
    {
        try
        {
            BufferedReader in = new BufferedReader(new InputStreamReader(peer.getInputStream(), "UTF-8"));
            String clientMsg;
            JSONParser parser = new JSONParser();
            while (true){
                if ((clientMsg=in.readLine())!=null){
                    System.out.println("receive the response from a peer");
                    System.out.println("Peer info: "+peer.toString());
                    JSONObject socketRequest = (JSONObject) parser.parse(clientMsg);
                    System.out.println("Peer request"+socketRequest.toString());
                    System.out.println("generating a thread for processing the request");
                    ServerPart server = new ServerPart("processing", socketRequest, ServerMain.fileSystemManager, peer);
                    server.process();
                }
                sleep(100);
            }

        }catch (ConnectException e){
            ServerMain.peerSocket.remove(peer);
            ServerMain.connectedPeerInfo.remove(ServerMain.peerSocket.get(peer));
            System.out.println("peer has been removed: "+peer.toString());
        }
        catch (SocketException e){
            ServerMain.peerSocket.remove(peer);
            ServerMain.connectedPeerInfo.remove(ServerMain.peerSocket.get(peer));
            System.out.println("peer has been removed: "+peer.toString());
        }
        catch (IOException e){
            e.printStackTrace();
        }catch (ParseException e){
            e.printStackTrace();
        }catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
