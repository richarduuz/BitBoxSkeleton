package unimelb.bitbox;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import unimelb.bitbox.util.Configuration;
import org.json.simple.parser.ParseException;

import javax.crypto.*;
import java.io.*;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.get;
import static com.google.common.collect.Iterables.size;

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
    private SecretKey secretKey;


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
                System.out.println("successful connection");
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"));
                String clientMsg = null;
                clientMsg = in.readLine();
                JSONObject Request = (JSONObject) parser.parse(clientMsg);
                JSONObject AUTH_RESPONSE = new JSONObject();
                String ClientRequest = (String) Request.get("command");
                if (ClientRequest.equals("AUTH_REQUEST"))
                {
                    System.out.println("recieve auth_request");
                    String identity=(String) Request.get("identity");
                    for(String keys:authorized_keys){
                        if (keys.split(" ")[2].equals(identity)){
                            // add a flag to detect whether the public key is found
                            flag = true;
                            String clientPublicKey=keys;
//                            String test = ("ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAACAQDFhO260GYWqHjE2APqs09KReSRSJbjzj7sCbjmIabDBXY2F9KTyN52eDdYPjrQibUbhmeyQqVOf4kdDxSVYLlpxIPfgUdcvjXw4OSZ65UxCpyTwBdYSCUHFqrAbvGgopl2sdibUyrRNsLJ8FT5Rq+M9ZHpoMjaLW4dnkaZq6hyDxxmtNluX7JVlzNWCfYO8Fmcii7REEur3BYq0CrH3WH8zFMGvvvoD+qmd6SPh8f4TLjZJ9TrarMnZ2HCqJDWqgTwMIZBwQ/fq3QPTgzZbZO90H3o1LM0q8MYgr5wRg2eAwL0+c89oI9OsOPyPDyeVD9Uw5NoQ7Re5LHahhms2KYDcfa7Z1sX7QONDcr/RlaIrW4rhekMy+FSu1FDmcQ64bqyj8TinYdaTe6TIybWIMTBVjV2vWGf5uP5JpXOmiw7gfPay22KC4xLVGRBQpiVM9KY8+AOekFX0s3TQSY5N9EOCVqU6mMahS04F4jUvBnYSMc8bb5M0GlVnafYHk2GHU/E88dsKeJC9vxgGcEndXwxhCsG5pfCtSqKfMz8bJOZl65fH8YpVSqe6q9w9YHNx/Qtiyh6/421o0JjFi2PUz+an11U+aUL9T8Itkh2B+PoGV4d9NtoqxGLE7iHhoXz+h3XCJbO9cj7BAJu/nyggIFU4PCAzC3JgTZMokkBEOCIiQ== QT@LAPTOP-442HE0V5");
//                            if (clientPublicKey.equals(test)){
//                                System.out.println(true);
//                            }
                            KeyGenerator kg=KeyGenerator.getInstance("AES");
                            kg.init(128);
                            SecretKey sk=kg.generateKey();
                            secretKey = sk;
                            AUTH_RESPONSE.put("command","AUTH_RESPONSE");
                            AUTH_RESPONSE.put("status",true);
                            AUTH_RESPONSE.put("message","public key found");
                            byte[] input = sk.getEncoded();
                            // Generate public key and encryption
                            Key publicKey = generatePublicKey(clientPublicKey);
                            Cipher cipher = Cipher.getInstance("RSA");
                            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
                            byte[] EncryptedAES = cipher.doFinal(input);
                            System.out.println("Conplete RSA Encryption");

                            //Base64 encoding
                            String secretKeyEncoded= Base64.getEncoder().encodeToString(EncryptedAES);


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
                else{
                        flag = false;
                        // Once connect to client, create a client monitor
                        ClientMonitor clientMonitor = new ClientMonitor("ClientSocket", clientSocket, secretKey);
                        clientMonitor.start();

                    }
                }
            }

        }catch (IOException e){
            //TODO process it
        }catch (NoSuchAlgorithmException e){
            //TODO process it
        }catch (ParseException e){
            //TODO process it
        }catch (NoSuchPaddingException e){
            e.printStackTrace();
        }catch (InvalidKeyException e){
            e.printStackTrace();
        }catch (IllegalBlockSizeException e){
            e.printStackTrace();
        }catch (BadPaddingException e){
            e.printStackTrace();
        }
    }

    private Key generatePublicKey(String key){
        // from https://www.oipapio.com/question-320700
        Key rsaKey = null;
        try{
            KeyFactory kf = KeyFactory.getInstance("RSA");
            String header = "ssh-rsa";
            ByteSource keysource = ByteSource.wrap(key.getBytes("UTF-8"));
            InputStream stream = keysource.openStream();
            Iterable<String> parts = Splitter.on(' ').split(IOUtils.toString(stream, String.valueOf(Charsets.UTF_8)));
            checkArgument(size(parts) >= 2 && header.equals(get(parts,0)), "bad format, should be ssh-rsa AAAAB3....");
            stream = new ByteArrayInputStream(Base64.getDecoder().decode(get(parts, 1)));
            String marker = new String(readLengthFirst(stream), "UTF-8");
            checkArgument(header.equals(marker), "looking for marker marker %s but received %s", header, marker);
            BigInteger publicCotent = new BigInteger(readLengthFirst(stream));
            BigInteger modulus = new BigInteger(readLengthFirst(stream));
            KeySpec keySpec = new RSAPublicKeySpec(modulus, publicCotent);
            rsaKey = kf.generatePublic(keySpec);


        }catch (NoSuchAlgorithmException e){
            //TODO Process NoSuchAlgorithmException
        }catch (UnsupportedEncodingException e){
            //TODO Processs UnsupportedEncodingException
        }catch (IOException e){
            //TODO Process IOException
        }catch (InvalidKeySpecException e){
            //TODO Process InvalidKeySpecException
        }
        return rsaKey;
    }


    public byte[] readLengthFirst(InputStream in){
        byte[] val = null;
        try{
            int[] bytes = new int[]{in.read(), in.read(), in.read(), in.read()};
            int length = 0;
            int shift = 24;
            for (int i = 0; i < bytes.length; i++){
                length += bytes[i] << shift;
                shift -= 8;
            }
            val = new byte[length];
            ByteStreams.readFully(in, val);
        }catch (IOException E){
            E.printStackTrace();
        }
        return val;
    }


}
