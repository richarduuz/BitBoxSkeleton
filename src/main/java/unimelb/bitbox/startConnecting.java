package unimelb.bitbox;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.testClient;


public class startConnecting extends Thread {
    public String host;
    public long port;
    public Thread t;
    private String ThreadName;

    public startConnecting(String Host,long Port,String name)
    {
        host=Host;
        port=Port;
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

    public void run(){
        try {
            Socket socket=new Socket(host,(int)port);
            if (ServerMain.peerSocket.get(socket)==null)
            {
                if(ServerMain.handshakeProcedure(socket,false))
                {
                    peerSocketMonitor socketMonitor=new peerSocketMonitor("socket",socket);
                    socketMonitor.start();
                }
                ArrayList<FileSystemManager.FileSystemEvent> tobesynced=ServerMain.fileSystemManager.generateSyncEvents();
                for (FileSystemManager.FileSystemEvent event:tobesynced){
                    testClient client=new testClient("client"+socket.toString(),socket,ServerMain.fileSystemManager,event);
                    client.start();
                }
                System.out.println("peer has been added");
            }
            else{
                socket.close();
                System.out.println("peer has already been connected");
            }
        }catch (ConnectException e){
            System.out.println("peer is not online "+"host: "+host+" port:"+port);
        }
        catch (IOException e){
            e.printStackTrace();
        }catch (NoSuchAlgorithmException e){
            e.printStackTrace();
        }
    }
}
