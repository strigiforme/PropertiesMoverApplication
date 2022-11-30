package com.client;

import com.utils.Files.PropertiesFile.BaseFilter;
import com.utils.Files.PropertiesFile.PropertiesFile;
import com.utils.Files.PropertiesFile.RegexFilter;
import com.utils.Logger.Logger;
import com.utils.Config.ConfigurationException;
import com.utils.BaseApplication;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.*;

/**
 * Manages dependency injection and application lifecycle for client
 *
 * @author Howard Pearce
 */
public class ClientApplication extends BaseApplication {
    /**
     * IP Address of Server
     */
    private String m_serverAddress = null;
    /**
     * Port that Server is listening on
     */
    private Integer m_serverPort = null;
    /**
     * Injected into ClientDirectoryManager
     */
    private final WatchService m_watchService = FileSystems.getDefault().newWatchService();
    /**
     * Injected into ClientDirectoryManager
     */
    private WatchKey m_watchKey = null;
    /**
     * Manages directory changes for us
     */
    private ClientDirectoryManager m_directoryManager = null;
    /**
     * Manages network communications
     */
    private ClientNetworkManager m_networkManager = null;
    /**
     * Regex key to filter properties on
     */
    private String m_filterPattern = null;
    /**
     * Object responsible for applying filter
     */
    private BaseFilter m_filter = null;
    /**
     * How long we should wait before re-connecting to server in ms
     */
    private Integer m_connectionDelay = null;


    /**
     * Constructor to read in service configuration
     *
     * @param configPath filepath to client configuration as String
     * @throws IOException if configuration cannot be opened
     */
    public ClientApplication(String configPath) throws IOException {
        super(configPath, "client");
    }

    public static void main(String[] args) {
        // validate args before we start
        String areArgsValid = areArgumentsValid(args);
        if (areArgsValid.length() > 0) {
            System.out.println(areArgsValid);
            return;
        }
        // args are good, begin initialization
        Logger.logInfo("Initializing client");

        // attempt to read configuration
        ClientApplication clientApplication = (ClientApplication) getConfiguredApplication(args[0]);
        // do not proceed if configuration failed
        if (clientApplication == null) {
            shutdown(null);
        }

        // inject resources into other classes
        try {
            clientApplication.initialize();
            Logger.logInfo("Successfully initialized Client.");
        } catch (IOException e) {
            Logger.logError("Error occurred while initializing:" + e.getMessage());
            shutdown(e);
        }

        // primary run loop
        Logger.logInfo("Starting clientApplication");
        clientApplication.run();
    }

    /**
     * Starts the primary execution loop for the ClientApplication
     */
    public void run() {
        try {
            m_directoryManager.watchForFileChanges();
        } catch (InterruptedException e) {
            Logger.logError("Application execution interrupted: " + e.getMessage());
        } catch (IOException e) {
            Logger.logError("IOException occurred while sending file: " + e.getMessage());
        }
    }

    /**
     * Initializes all resources required for ClientApplication to start
     */
    public void initialize() throws IOException {
        try {
            this.initializeClientDirectoryManager();
            Logger.logInfo("Created clientDirectoryManager successfully");
            this.initializeClientNetworkManager();
            Logger.logInfo("Created clientNetworkManager successfully.");
        } catch (IOException e) {
            Logger.logError("Error occurred while initializing resources: " + e.getMessage());
            shutdown(e);
        }
    }

    /**
     * Used by ClientDirectoryManager to send propertiesFile to server
     *
     * @param fileToSend file that will be sent to the server
     * @throws IOException if send fails due to network error
     */
    protected void sendPropertiesFileMessage(PropertiesFile fileToSend) throws IOException {
        try {
            m_networkManager.sendPropertiesFile(fileToSend);
        } catch (IOException e) {
            // do not need to shut down for single failure
            Logger.logError("Failed to send message to server. " + e.getMessage());
            e.printStackTrace(System.out);
        }
    }

    /**
     * Generate required resources for an instance of clientNetworkManager
     *
     * @throws IOException if initialization of manager fails due to network error
     */
    public void initializeClientNetworkManager() throws IOException {
        Socket clientSocket = ClientNetworkManager.connectToServer(m_serverAddress, m_serverPort, m_connectionDelay);
        ObjectOutputStream outputStream = new ObjectOutputStream(clientSocket.getOutputStream());
        m_networkManager = new ClientNetworkManager(m_serverAddress, m_serverPort, clientSocket, outputStream);
    }

    /**
     * Generate required resources for an instance of clientDirectoryManager
     *
     * @throws IOException if initialization fails due to filesystem error
     */
    public void initializeClientDirectoryManager() throws IOException {
        Path directory = Paths.get(m_directory);
        m_watchKey = directory.register(m_watchService, StandardWatchEventKinds.ENTRY_CREATE);
        m_filter = new RegexFilter(m_filterPattern);
        m_directoryManager = new ClientDirectoryManager(m_watchService, m_watchKey, this, m_directory, m_filter);
    }

    /**
     * Read in the arguments provided by the user and validate them.
     *
     * @param args the arguments from the main method
     * @return A string that describes what is wrong with the arguments provided.
     */
    public static String areArgumentsValid(String[] args) {
        if (args.length != 1) {
            return "Invalid number of arguments provided. See readme for more info on how to run.";
        }
        return "";
    }

    /**
     * Read in specific configuration items for the client. Does not verify ranges or values for verification.
     *
     * @throws ConfigurationException If configuration can not be found
     */
    public void readConfiguration() throws ConfigurationException {
        super.readConfiguration(m_configurationManager);
        m_serverAddress = m_configurationManager.getConfigItemAsString(m_applicationName + ".serverAddress");
        m_serverPort = m_configurationManager.getConfigItemAsInteger(m_applicationName + ".serverPort");
        m_filterPattern = m_configurationManager.getConfigItemAsString(m_applicationName + ".filterPattern");
        m_connectionDelay = m_configurationManager.getConfigItemAsInteger(m_applicationName + ".connectionDelay");
    }

    /**
     * Method to be called when closing clientApplication. Should be called if normally or abnormally exiting.
     */
    public static void shutdown(Exception e) {
        if (e != null) {
            e.printStackTrace(System.out);
        }
        System.out.println("Shutting down.");
    }
}