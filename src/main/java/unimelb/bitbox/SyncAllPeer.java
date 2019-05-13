package unimelb.bitbox;

import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.testClient;
import java.io.IOException;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.TimerTask;


public class SyncAllPeer extends TimerTask {
    private FileSystemManager fileSystemManager= ServerMain.fileSystemManager;
    private Socket peerSocket;

    public SyncAllPeer(Socket peer)
    {
        peerSocket=peer;
    }

    @Override
    public void run()
    {
        try {
            ArrayList<FileSystemManager.FileSystemEvent> tobeSynced=fileSystemManager.generateSyncEvents();
            for (FileSystemManager.FileSystemEvent event:tobeSynced){
                testClient client=new testClient("client"+peerSocket.toString(),peerSocket,fileSystemManager,event);
                client.start();
            }
        }catch (IOException e)
        {
            e.printStackTrace();
        }catch (NoSuchAlgorithmException e){
            e.printStackTrace();
        }
    }
}
