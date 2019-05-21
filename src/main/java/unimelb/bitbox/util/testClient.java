package unimelb.bitbox.util;

import org.json.simple.JSONObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import org.json.simple.parser.JSONParser;


public class testClient extends Thread{
    private Thread t;
    private String ThreadName;
    private FileSystemManager.FileSystemEvent tobeprocessed;
    private String advertisedName;
    private int port;
    private Socket socket;
    protected FileSystemManager fileSystemManager;


    public testClient(String name,Socket inputSocket,FileSystemManager manager, FileSystemManager.FileSystemEvent tobeProcessed)throws NumberFormatException, IOException, NoSuchAlgorithmException
    {
        ThreadName=name;
        socket=inputSocket;
        fileSystemManager=manager;
        tobeprocessed=tobeProcessed;
    }

    public static JSONObject generateRequest(FileSystemManager.FileSystemEvent fileSystemEvent){
        JSONObject REQUEST=new JSONObject();
        switch (fileSystemEvent.event)
        {
            case DIRECTORY_CREATE:
            {
                REQUEST.put("command",fileSystemEvent.event+"_REQUEST");
                REQUEST.put("pathName",fileSystemEvent.pathName);
                break;
            }
            case DIRECTORY_DELETE:
            {
                REQUEST.put("command",fileSystemEvent.event+"_REQUEST");
                REQUEST.put("pathName",fileSystemEvent.pathName);
                break;
            }
            case FILE_CREATE:
            {
                JSONObject fileDescriptor=new JSONObject();
                fileDescriptor.put("md5",fileSystemEvent.fileDescriptor.md5);
                fileDescriptor.put("lastModified",fileSystemEvent.fileDescriptor.lastModified);
                fileDescriptor.put("fileSize",fileSystemEvent.fileDescriptor.fileSize);
                REQUEST.put("command",fileSystemEvent.event+"_REQUEST");
                REQUEST.put("fileDescriptor",fileDescriptor);
                REQUEST.put("pathName",fileSystemEvent.pathName);
                break;
            }
            case FILE_DELETE:
            {
                JSONObject fileDescriptor=new JSONObject();
                fileDescriptor.put("md5",fileSystemEvent.fileDescriptor.md5);
                fileDescriptor.put("lastModified",fileSystemEvent.fileDescriptor.lastModified);
                fileDescriptor.put("fileSize",fileSystemEvent.fileDescriptor.fileSize);
                REQUEST.put("command",fileSystemEvent.event+"_REQUEST");
                REQUEST.put("fileDescriptor",fileDescriptor);
                REQUEST.put("pathName",fileSystemEvent.pathName);
                break;
            }
            case FILE_MODIFY:
            {
                JSONObject fileDescriptor=new JSONObject();
                fileDescriptor.put("md5",fileSystemEvent.fileDescriptor.md5);
                fileDescriptor.put("lastModified",fileSystemEvent.fileDescriptor.lastModified);
                fileDescriptor.put("fileSize",fileSystemEvent.fileDescriptor.fileSize);
                REQUEST.put("command",fileSystemEvent.event+"_REQUEST");
                REQUEST.put("fileDescriptor",fileDescriptor);
                REQUEST.put("pathName",fileSystemEvent.pathName);
                break;
            }
        }
        return REQUEST;
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

        try {
            JSONParser parser=new JSONParser();
            // Get the input/output streams for reading/writing data from/to the socket
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
            JSONObject REQUEST = generateRequest(this.tobeprocessed);
            out.write(REQUEST.toJSONString() + '\n');
            System.out.println("Sending a request");
            System.out.println("request content: "+REQUEST.toString()+"\n");
            out.flush();

        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
