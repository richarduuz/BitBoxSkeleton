package unimelb.bitbox;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import unimelb.bitbox.util.*;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

import java.io.*;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Logger;



public class ServerMain extends Thread implements FileSystemObserver {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	public static long udpPort = Long.parseLong(Configuration.getConfigurationValue("udpPort"));
	public static FileSystemManager fileSystemManager;
	public static final int maximumIncommingConnections = Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
	private int clientNumber = 0;
	private int port = Integer.parseInt(Configuration.getConfigurationValue("port"));
//	public static ArrayList<FileSystemEvent> eventList = new ArrayList<>();
	public static Map<Socket,String[]> peerSocket = new HashMap<>();
	public static Queue<FileSystemEvent> tobeprocessed = new LinkedList<FileSystemEvent>();
	public static ArrayList<String[]> connectedPeerInfo=new ArrayList<>();//[0] host, [1] port
    public static String mode = Configuration.getConfigurationValue("mode");
    public static final int maximumRetryNumbers = Integer.parseInt(Configuration.getConfigurationValue("maximumRetryNumber"));
	public static int blockSize = Integer.parseInt(Configuration.getConfigurationValue("blockSize"));
	public static ArrayList<String[]> rememberPeer = new ArrayList<>();
	public static ArrayList<String[]> onlinePeer = new ArrayList<>();
	public static String myhost = Configuration.getConfigurationValue("advertisedName");
	public static int TimeOutPeriod = Integer.parseInt(Configuration.getConfigurationValue("timeoutPeriod"));
	public static int syncInterval = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));



	public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {


	    if (mode.equals("tcp")) {


            fileSystemManager = new FileSystemManager(Configuration.getConfigurationValue("path"), this);
            ServerSocket clientSocket = new ServerSocket(Integer.parseInt(Configuration.getConfigurationValue("clientPort")));

            ///start a therad to listening the client port///
            AcceptClient clientConnection = new AcceptClient("listening client", clientSocket);
            clientConnection.start();
            ///finish///

            String[] peerPortInfo = Configuration.getConfigurationValue("peers").split(",");
            String[] peerHostInfo = Configuration.getConfigurationValue("advertisedName").split(",");
            ServerSocket listenSocket = new ServerSocket(port);

            ///start a thread to listening the port///
            acceptConnection connection = new acceptConnection("listening socket", listenSocket);
            connection.start();
            ///finish///
            try {
                for (int i = 0; i < peerPortInfo.length; i++) {
                    String peerPort = peerPortInfo[i].split(":")[1];
                    String peerHost = peerHostInfo[i];
                    startConnecting startconnecting = new startConnecting(peerHost, Long.parseLong(peerPort), "peer" + i);
                    startconnecting.start();
                    sleep(1000);
                }


                while (true) {
                    if (tobeprocessed.size() > 0) {
                        System.out.println("generating thread to process this event");
                        System.out.println("event content: " + tobeprocessed.element().toString() + "\n");
                        int peerCounter = 0;
                        for (Socket peer : peerSocket.keySet()) {
                            if (peer != null) {
                                if (peerCounter != peerSocket.size() - 1) {
                                    testClient client = new testClient("client " + clientNumber, peer, fileSystemManager, tobeprocessed.element());
                                    client.start();
                                } else {
                                    testClient client = new testClient("client " + clientNumber, peer, fileSystemManager, tobeprocessed.poll());
                                    client.start();
                                }
                                peerCounter++;
                            }
                        }
                    }
                    sleep(1000);
                }
            } catch (SocketException ex) {
                ex.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

        else if (mode.equals("udp")) {
	    	if (ServerMain.blockSize > 8192){
	    		ServerMain.blockSize = 8192;
			}
			// UDP Process
			fileSystemManager = new FileSystemManager(Configuration.getConfigurationValue("path"), this);
			ServerSocket clientSocket = new ServerSocket(Integer.parseInt(Configuration.getConfigurationValue("clientPort")));

			///start a therad to listening the client port///
			AcceptClient clientConnection = new AcceptClient("listening client", clientSocket);
			clientConnection.start();
			///finish///
			long UDP_Port = Long.parseLong(Configuration.getConfigurationValue("udpPort"));

			DatagramSocket socket = new DatagramSocket((int) UDP_Port);
			// Start listening

			Udp_Server UDPServer = new Udp_Server("server", socket);

			UDPServer.start();

			// Start client
			String[] peerPortInfo = Configuration.getConfigurationValue("peers").split(",");
			long[] portInfor = new long[peerPortInfo.length];
			String[] peerHostInfo = new String[peerPortInfo.length];
			for (int i = 0; i < peerPortInfo.length; i++) {
				portInfor[i] = Long.parseLong(peerPortInfo[i].split(":")[1]);
				peerHostInfo[i] = peerPortInfo[i].split(":")[0];
			}

			// handshake

			for (int i = 0; i < peerHostInfo.length; i++){
				String host = peerHostInfo[i];
				long peerPort = portInfor[i];
				DatagramPacket handshakePacket = handshakePacket(host, peerPort);
				System.out.println("host: " + host + " port: " + peerPort);
				UDPClient handshakeClient = new UDPClient("handshake",handshakePacket, "HANDSHAKE_REQUEST");
				handshakeClient.start();
				System.out.println("Starting handshake");
			}


			try {
				sleep(1000);
				while (true) {
					if (tobeprocessed.size() > 0 && onlinePeer.size()>0) {
						System.out.println("Sending request for new event");
//						for (int i = 0; i < peerHostInfo.length; i++) {
//							String host = peerHostInfo[i];
//							long port = portInfor[i];
//							DatagramPacket handshakePacket = handshakePacket(host, port);
//							UDPClient handshakeClient = new UDPClient("handshake",handshakePacket, "HANDSHAKE_REQUEST");
//							handshakeClient.start();
//						}
//						for (String[] peer: rememberPeer){
//							String host = peer[0];
//							long port = Long.parseLong(peer[1]);
//							DatagramPacket handshakePacket = handshakePacket(host, port);
//							UDPClient handshakeClient = new UDPClient("handshake",handshakePacket, "HANDSHAKE_REQUEST");
//							handshakeClient.start();
//						}
//						for (String[] peer: rememberPeer){
//							boolean flag = false;
//							for (String[] peerInfo: ServerMain.onlinePeer){
//								if (peer[0].equals(peerInfo[0])){
//									flag = true;
//								}
//							}
//							if (!flag){
//								onlinePeer.add(peer);
//							}
//						}
						for (int i = 0; i < onlinePeer.size(); i++){
							String host = (onlinePeer.get(i))[0];
							long port = Long.parseLong(onlinePeer.get(i)[1]);
							if ((i != onlinePeer.size()-1)){
								UDPClient udpClient = new UDPClient("client", host, port, tobeprocessed.element());
								udpClient.start();
							}
							else {
								UDPClient udpClient = new UDPClient("client", host, port, tobeprocessed.poll());
								udpClient.start();
							}
						}

					}
					else{
						tobeprocessed.clear();
					}
					sleep(1000);
				}

			}catch (InterruptedException e){
			    e.printStackTrace();
			}
		}

	}

	@Override
	public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
		// TODO: process events
		tobeprocessed.offer(fileSystemEvent);
		System.out.println("new event is formed");
		System.out.println("event content: "+fileSystemEvent.toString()+"\n");
	}

	public static DatagramPacket handshakePacket(String host, long port){
		DatagramPacket packet = null;
		try{
			JSONObject handshake = new JSONObject();
			handshake.put("command", "HANDSHAKE_REQUEST");
			JSONObject hostPort = new JSONObject();
			hostPort.put("host", myhost);
			hostPort.put("port",udpPort);
			handshake.put("hostPort", hostPort);
			InetAddress address = InetAddress.getByName(host);
			packet = new DatagramPacket(handshake.toJSONString().getBytes("UTF-8"), handshake.toJSONString().getBytes("UTF-8").length, address, (int)port);
		}catch (UnsupportedEncodingException e){
			e.printStackTrace();
		}catch (UnknownHostException e){
			e.printStackTrace();
		}
		return packet;
	}

	public static boolean handshakeProcedure(Socket socket,boolean isSpare) {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
			String inputStr = "HANDSHAKE_REQUEST";
			JSONObject Request = new JSONObject();
			JSONObject hostPort = new JSONObject();
			hostPort.put("host", socket.getLocalAddress().getHostName());
			hostPort.put("port", Long.parseLong(Configuration.getConfigurationValue("port")));
			Request.put("command", inputStr);
			Request.put("hostPort", hostPort);
			out.write(Request.toJSONString() + '\n');
			System.out.println("Sending handshake request to "+socket.toString()+"\n");
			out.flush();
			String ServerMsg = in.readLine();
			JSONParser parser = new JSONParser();
			JSONObject Response = (JSONObject) parser.parse(ServerMsg);
			String ServerResponse_command = (String) Response.get("command");
			System.out.println("get the response after the handshake request: "+ServerResponse_command+"\n");

			//if the command is invalid
			if (ServerResponse_command.equals("INVALID_PROTOCOL")) {
				String ServerResponse_message = (String) Response.get("message");
				System.out.println(ServerResponse_message+"\n");
				socket.close();
			} else if (ServerResponse_command.equals("CONNECTION_REFUSED")) {
				if(!isSpare){
				JSONArray peers = (JSONArray) Response.get("peers");
				Iterator<JSONObject> peer = peers.iterator();
				while (peer.hasNext()) {
					JSONObject exactPeer = peer.next();
					System.out.println("trying to connect another peer"+"\n");
					Socket spareSocket = new Socket((String) exactPeer.get("host"), (int)((long)exactPeer.get("port")));
					if(handshakeProcedure(spareSocket,true))
					{
						peerSocketMonitor socketMonitor=new peerSocketMonitor("socket",spareSocket);
						socketMonitor.start();
						ArrayList<FileSystemManager.FileSystemEvent> tobesynced=ServerMain.fileSystemManager.generateSyncEvents();
						for (FileSystemManager.FileSystemEvent event:tobesynced){
							testClient client=new testClient("client"+socket.toString(),spareSocket,ServerMain.fileSystemManager,event);
							client.start();
						}

						break;
					}

				}
				}
				else{
					return false;
				}
			}
			else {
				JSONObject Server_hostPort = (JSONObject) Response.get("hostPort");

				String Server_host = (String)Server_hostPort.get("host");
				long Server_port = (long) Server_hostPort.get("port");
				System.out.println("the connection is established");
				System.out.println("host : " + Server_host);
				System.out.println("port : " + Server_port+"\n");
				peerSocket.put(socket,new String[]{Server_host,String.valueOf(Server_port)});
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}catch (NoSuchAlgorithmException e){
			e.printStackTrace();
		}
		return true;
	}
}

