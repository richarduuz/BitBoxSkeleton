package unimelb.bitbox;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import unimelb.bitbox.util.Configuration;
import org.json.simple.parser.ParseException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Created by ysy on 13/5/19.
 */
public class AcceptClient extends Thread {
    private ServerSocket socket;
    private String ThreadName;
    private Thread t;
    private Socket clientSocket;
    private String[] authorized_keys=Configuration.getConfigurationValue("authorized_keys").split(",");
    private SecretKey sk;
    private boolean flag = false;


    AcceptClient(String name, ServerSocket serverSocket){
        ThreadName=name;
        socket=serverSocket;
    }

    public void start(){
        if (t==null)
        {
            t=new Thread(this,ThreadName);
            t.start();
        }
    }

    public void run(){
        try{
            while(true)
            {
                JSONParser parser = new JSONParser();
                clientSocket=socket.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"));
                String clientMsg = null;
                clientMsg = in.readLine();
                JSONObject Request = (JSONObject) parser.parse(clientMsg);
                JSONObject AUTH_RESPONSE = new JSONObject();
                String ClientRequest = (String) Request.get("command");
                if (ClientRequest.equals("AUTH_REQUEST"))
                {
                    String identity=(String) Request.get("identity");
                    for(String keys:authorized_keys){
                        if (keys.split(" ")[2].equals(identity)){
                            // add a flag to detect whether the public key is found
                            flag = true;
                            String clientPublicKey=keys.split(" ")[1];
                            KeyGenerator kg=KeyGenerator.getInstance("AES");
                            kg.init(128);
                            SecretKey sk=kg.generateKey();
                            AUTH_RESPONSE.put("command","AUTH_RESPONSE");
                            AUTH_RESPONSE.put("status",true);
                            AUTH_RESPONSE.put("message","public key found");
                            byte[] input = sk.getEncoded();
                            String secretKeyEncoded= Base64.getEncoder().encodeToString(input);
                            AUTH_RESPONSE.put("AES128",secretKeyEncoded);
                            out.write(AUTH_RESPONSE.toJSONString()+'\n');
                            out.flush();
                        }
                    }
                    if (!flag){
                        AUTH_RESPONSE.put("command", "AUTH_RESPONSE");
                        AUTH_RESPONSE.put("status", false);
                        AUTH_RESPONSE.put("message", "public key not found");
                        out.write(AUTH_RESPONSE.toJSONString() + '\n');
                        out.flush();
                }
                flag = false;
                }
            }

        }catch (IOException e){
            //TODO process it
        }catch (NoSuchAlgorithmException e){
            //TODO process it
        }catch (ParseException e){
            //TODO process it
        }
    }
}
