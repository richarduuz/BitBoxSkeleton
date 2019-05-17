package unimelb.bitbox;

import org.kohsuke.args4j.Option;

public class cmdLineArgs {
    @Option(required = true, name = "-c", aliases = {"--command"}, usage = "command")
    private String command;

    @Option(required = true, name = "-s", aliases = {"--server"}, usage = "Server")
    private String Server;


    @Option(required = false, name = "-p", aliases = {"--peer"},usage = "Peer")
    private String Peer;

    public String getCommand() {
        return command;
    }

    public String getServer() {
        return Server;
    }

    public String getPeer() {
        return Peer;
    }
}
