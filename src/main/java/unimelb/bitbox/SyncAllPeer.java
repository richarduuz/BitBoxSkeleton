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
    private String host;
    private String port;

    public SyncAllPeer(String peerHost, String peerPort)
    {
        host = peerHost;
        port = peerPort;
    }


    @Override
    public void run()
    {
        ArrayList<FileSystemManager.FileSystemEvent> tobeSynced=fileSystemManager.generateSyncEvents();
        for (FileSystemManager.FileSystemEvent event:tobeSynced) {
            UDPClient client = new UDPClient("client", host, Long.parseLong(port), event);
            client.start();
        }
    }
}
