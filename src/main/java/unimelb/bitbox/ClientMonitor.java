package unimelb.bitbox;

import com.sun.org.apache.xalan.internal.xsltc.runtime.InternalRuntimeError;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import unimelb.bitbox.ServerMain;
import unimelb.bitbox.ServerPart;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;

/**
 * Created by ysy on 13/5/19.
 */
public class ClientMonitor extends Thread {
    private String ThreadName;
    private Thread t;
    private ServerSocket clientSocket;

    ClientMonitor(String name,ServerSocket socket)
    {
        ThreadName=name;
        clientSocket=socket;
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
        try
        {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
            String clientMsg;
            JSONParser parser = new JSONParser();
            while (true){
                if ((clientMsg=in.readLine())!=null){
                    System.out.println("receive the response from a peer");
                    System.out.println("Peer info: "+clientSocket.toString());
                    JSONObject socketRequest = (JSONObject) parser.parse(clientMsg);
                    System.out.println("Peer request"+socketRequest.toString());
                    System.out.println("generating a thread for processing the request");
                    //TODO process the message from the client

                    //ServerPart server = new ServerPart("processing", socketRequest, ServerMain.fileSystemManager, clientSocket);
                    //server.process();
                }
                sleep(100);
            }

        }catch (ParseException e){
            //TODO process io exception
        }catch (IOException e){
            //TODO process parser exception
        }catch (InterruptedException e){
            //TODO process interrupt exception
        }
    }
}
